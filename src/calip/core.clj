(ns calip.core
  (:require [robert.hooke :as hooke]))

(defn default-format [{:keys [fname took args]}]
  (format "\"%s\" %s took: %,d nanos"
          fname args took))

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
             :args args})
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
   (doseq [f fs]
     (hooke/add-hook f                            ;; target var
                     (str f)                      ;; hooke key
                     (partial m-fn opts f))))))   ;; wrapper

(defn uncalip [fs]
  "takes a set of functions (namespace vars) and removes times from them.
   i.e. (uncalip #{#'app/foo #'app/bar})"
  (doseq [f fs]
    (hooke/clear-hooks f)))
