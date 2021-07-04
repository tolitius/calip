(ns calip.core
  (:require [clojure.string :as s]
            [robert.hooke :as hooke]))

(defn default-format [{:keys [fname took args returned error]}]
  (if-not error
    (format "\"%s\" args: %s | took: %,d nanos | returned: %s"
            fname args took returned)
    (format "\"%s\" args: %s | took: %,d nanos | error: %s"
            fname args took error)))

(defn default-report [results]
  (println (default-format results)))

(defn- calip [{:keys [report]
               :or {report default-report}} fname f & args]
  "wraps a function call with a timer: i.e. that times the function execution
   and reports the result"
  (let [start (System/nanoTime)
        v (apply f args)
        took (- (System/nanoTime) start)]
    (report {:took took
             :fname fname
             :args args
             :returned v})
    v))

(defn- on-error [{:keys [report]
                  :or {report default-report}} fname f & args]
  "wraps a function call in a try/catch with a timer
  in case of na error reports a runtime function state (i.e. arguments)
  and how long the function execution took"
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

(defn f-starts-with [with x]
  (let [[_ f] (-> x str (s/split #"/"))]
    (s/starts-with? f with)))

(defn expand-some-vars [f]
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
  (set (mapcat f-to-fs fs)))

(defn measure
  "takes a set of functions (namespace vars) with 'optional options'
   and wraps them with timers.

   i.e. (measure #{#'app/foo #'app/bar})
                        or
        (measure #{#'app/foo #'app/bar} {:report log/info})

  by default 'measure' will use 'println' to report times functions took"
  ([fs]
   (measure fs {}))
  ([fs {:keys [on-error?] :as opts}]
   (let [m-fn (if on-error?
                on-error
                calip)]
     (doseq [f (unwrap-stars fs)]
       (let [fvar (f-to-var f)]
         (println "wrapping" fvar)
         (hooke/add-hook fvar                             ;; target var
                         (str fvar)                       ;; hooke key
                         (partial m-fn opts fvar)))))))   ;; wrapper

(defn uncalip [fs]
  "takes a set of functions (namespace vars) and removes times from them.
   i.e. (uncalip #{#'app/foo #'app/bar})"
  (doseq [f (unwrap-stars fs)]
    (hooke/clear-hooks
      (f-to-var f))
    (println "remove a wrapper from" f)))
