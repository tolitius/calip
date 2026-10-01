(ns calip.core
  (:require [clojure.string :as s]
            [com.brunobonacci.mulog :as u]
            [robert.hooke :as hooke]))

(def ^:dynamic *silent* false)

(def ^:private measured
  (atom {}))  ;; {#'app/foo #{:measure :trace ...}}

(defn- record [fvar id]
  (swap! measured
         update fvar (fnil conj #{}) id))

(defn- retract [fvar id]
  (swap! measured
         (fn [m]
           (let [ids (disj (get m fvar #{}) id)]
             (if (seq ids)
               (assoc m fvar ids)
               (dissoc m fvar))))))

(defn wrapped
  "returns all the functions wrapped by calip with ids of their wrappers
   => {#'user/rsum #{:measure :trace}}"
  []
  @measured)

(defn default-format [{:keys [fname took args returned error]}]
  (if-not error
    (format "\"%s\" args: %s | took: %,d nanos | returned: %s"
            fname args took returned)
    (format "\"%s\" args: %s | took: %,d nanos | error: %s"
            fname args took error)))

(defn default-report [results]
  (when-not *silent*
    (println (default-format results))))

(defn- calip
  "wraps a function call with a timer: i.e. that times the function execution
   and reports the result"
  [{:keys [report]
    :or {report default-report}} fname f & args]
  (let [start (System/nanoTime)
        v (apply f args)
        took (- (System/nanoTime) start)]
    (report {:took took
             :fname fname
             :args args
             :returned v})
    v))

(defn- on-error
  "wraps a function call in a try/catch with a timer
   in case of an error reports a runtime function state (i.e. arguments)
   and how long the function execution took"
  [{:keys [report]
    :or {report default-report}} fname f & args]
  (let [start (System/nanoTime)]
    (try
      (apply f args)
      (catch Throwable t
        (let [took (- (System/nanoTime) start)]
          (report {:took took
                   :fname fname
                   :args args
                   :error t})
          (throw t))))))

(defn- var->str->symbol
  "will convert var (i.e. #'app.foo/bar)
   or a stringed var (i.e. \"#'app.foo/bar\") to a symbol"
  [v]
  (->> (str v)
       (drop 2)     ;; dropping "#'"
       (apply str)
       symbol))

(defn- var->keyword
  "will convert var (i.e. #'app.foo/bar)
   to a keyword :app.foo/bar"
  [v]
  (->> v
       var->str->symbol
       str
       keyword))

(defn- f-to-var
  "makes sure a function 'f' is a resolvable var
   returns the resolved var
   in case the var can't be resolved, throws a runtime exception"
  [f]
  (let [v (-> (var->str->symbol f)
              resolve)]
    (or v
        (throw (RuntimeException. (str "could not resolve \"" f "\". "
                                       "check the namespace prefix, function name spelling, etc. "
                                       "pass a fully qualified function name (i.e. \"#'app.foo/far\")"))))))

(defn- expand-all-vars [f]
  (-> (s/split f #"/")
      first
      (s/replace #"#|'" "")
      symbol
      ns-publics
      (->> (map second))))

(defn- f-starts-with [with x]
  (let [[_ f] (-> x str (s/split #"/"))]
    (s/starts-with? f with)))

(defn- expand-some-vars [f]
  (let [[_ fs] (s/split f #"/")        ;; #'foo.bar/baz-* will split as ["#'foo.bar" "baz-*"]
        [prefix _] (s/split fs #"\*")  ;; ["bar-" "*"]
        all-fs (expand-all-vars f)]
    (filterv (partial f-starts-with prefix)
             all-fs)))

(defn- f-to-fs
  "converts a namespace/var string in a \"#'foo.bar/*\" format
   to a sequence of all the vars/functions in that namespace

   => (calip/f-to-fs \"#'user/*\")
   (#'user/log4b #'user/dev #'user/check-sources #'user/rsum #'user/rmult #'user/+version+)"
  [f]
  (cond
    (and (string? f)
         (s/ends-with? f "/*"))  (expand-all-vars f)
    (and (string? f)
         (s/ends-with? f "*"))   (expand-some-vars f)
    :else [f]))

(defn- unwrap-stars
  "unwraps the stars: #'foo.bar/* to a set of all functions in that namespace"
  [fs]
  (set (mapcat f-to-fs (if (map? fs)          ;; i.e. (calip/wrapped)
                         (keys fs)
                         fs))))

(defn- wrappable?
  "only functions can be wrapped: not values, macros, multimethods, etc."
  [fvar]
  (and (fn? @fvar)
       (not (:macro (meta fvar)))))

(defn- pairs-with-args [{:keys [pairs format-args]}
                        args]
  (if-not format-args
    pairs
    (let [fargs (format-args args)]
      (->> (if (map? fargs)
             (into [] cat fargs)            ;; {:a 42 :b 34} => [:a 42 :b 34]
             [:args (format-args args)])
           (apply merge (or pairs []))))))

(defn- make-trace [{:keys [event-name]
                    :as opts}
                   fun-name
                   f & args]
  (let [pairs (pairs-with-args opts args)
        mops (-> (dissoc opts :format-args
                              :event-name)
                 (assoc :pairs pairs))]
    (u/trace (or event-name
                 (var->keyword fun-name))
             mops
             (apply f args))))

(defn wrap
  "takes a set of functions (namespace vars) and wraps them in an 'advice' function
   that is called _instead_ of the function with the function name (var),
   the function itself and its arguments: (fn [fname f & args] ...)

   it is up to the 'advice' to call (or not to call) the function:

   => (wrap #{#'user/rsum}
            (fn [fname f & args]
              (println \"calling\" fname \"with\" args)
              (apply f args)))

   a function can be wrapped many times as long as wrappers have different ids.
   by default an id is :wrap, but it can be provided:

   => (wrap #{#'user/rsum} retry {:id :retry})"
  ([fs advice]
   (wrap fs advice {}))
  ([fs advice {:keys [id]
               :or {id :wrap}}]
   (let [fvars (->> (unwrap-stars fs)
                    (map f-to-var)
                    (filter (fn [fvar]
                              (or (wrappable? fvar)
                                  (when-not *silent*
                                    (println "skipping" fvar "since it is not a function"))))))]
     (doseq [fvar fvars]
       (when-not *silent*
         (println "wrapping" fvar "in" id))
       (hooke/add-hook fvar                       ;; target var
                       [::calip id]               ;; hooke key
                       (partial advice fvar))     ;; wrapper
       (record fvar id))
     (set fvars))))

(defn unwrap
  "takes a set of functions (namespace vars) and removes calip wrappers from them.
   if an :id is provided, only removes a wrapper with this id

   i.e. (unwrap #{#'app/foo #'app/bar})
        (unwrap #{#'app/foo #'app/bar} {:id :retry})
        (unwrap (wrapped))"
  ([fs]
   (unwrap fs {}))
  ([fs {:keys [id]}]
   (doseq [fvar (map f-to-var (unwrap-stars fs))
           :let [ids (get @measured fvar #{})]
           wid (if id
                 (filter ids [id])
                 ids)]
     (hooke/remove-hook fvar [::calip wid])
     (retract fvar wid)
     (when-not *silent*
       (println "remove" wid "wrapper from" fvar)))))

(defn trace
  "takes a set of functions (namespace vars) with 'optional options'
   and wraps them µ/trace (https://github.com/BrunoBonacci/mulog#%CE%BCtrace)

   => (trace #{#'user/rsum
               #'user/rmult})

   in case µ/trace options are not provided, but the can be

  => (trace #{#'user/rsum
              #'user/rmult} {:pairs [:moo :zoo]               ;; standard µ/trace :pairs
                             :capture (fn [x] {:x-is x})      ;; ----- || ------- :capture
                             :format-args (comp s/upper-case  ;; if provided will also format and add input args to pairs
                                                str
                                                first)}}))"
  ([fs]
   (trace fs {}))
  ([fs {:keys [id] :as opts}]
   (wrap fs
         (partial make-trace (dissoc opts :id))
         {:id (or id :trace)})))

(defn measure
  "takes a set of functions (namespace vars) with 'optional options'
   and wraps them with timers.

   i.e. (measure #{#'app/foo #'app/bar})
                        or
        (measure #{#'app/foo #'app/bar} {:report log/info})

  by default 'measure' will use 'println' to report times functions took"
  ([fs]
   (measure fs {}))
  ([fs {:keys [on-error? id] :as opts}]
   (wrap fs
         (partial (if on-error? on-error calip) opts)
         {:id (or id :measure)})))

(defn uncalip
  "takes a set of functions (namespace vars) and removes all calip wrappers from them.
   i.e. (uncalip #{#'app/foo #'app/bar})"
  [fs]
  (unwrap fs))

(defn untrace
  "takes a set of functions (namespace vars) and removes µ/trace from them.
   i.e. (untrace #{#'app/foo #'app/bar})"
  [fs]
  (unwrap fs {:id :trace}))
