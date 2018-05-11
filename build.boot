(def +version+ "0.1.3")

(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure             "1.8.0"]
                  [robert/hooke                    "1.3.0"]

                  ;; test deps
                  [ch.qos.logback/logback-classic  "1.1.3"   :scope "test"]
                  [org.clojure/tools.logging       "0.3.1"   :scope "test"]

                  ;; boot clj
                  [boot/core                       "2.7.1"   :scope "provided"]
                  [adzerk/bootlaces                "0.1.13"  :scope "test"]
                  [adzerk/boot-logservice          "1.2.0"   :scope "test"]
                  [adzerk/boot-test                "1.1.1"   :scope "test"]
                  [tolitius/boot-check             "0.1.4"   :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :as bt]
         '[adzerk.boot-logservice :as log-service]
         '[tolitius.boot-check :as check]
         '[clojure.tools.logging :as log])

(bootlaces! +version+)

(def log4b
  [:configuration
   [:appender {:name "STDOUT" :class "ch.qos.logback.core.ConsoleAppender"}
    [:encoder [:pattern "%-5level %logger{36} - %msg%n"]]]
   [:root {:level "TRACE"}
    [:appender-ref {:ref "STDOUT"}]]])

(deftask dev []
  (alter-var-root #'log/*logger-factory*
                  (constantly (log-service/make-factory log4b)))
  (repl))

(deftask check-sources []
  ;; (set-env! :source-paths #(conj % "dev/clj" "dev/cljs" "test/core" "test/clj" "test/cljs"))
  (comp
    (check/with-bikeshed)
    (check/with-eastwood)
    (check/with-yagni)
    (check/with-kibit)))

(task-options!
  push {:ensure-branch nil}
  pom {:project     'tolitius/calip
       :version     +version+
       :description "functions deserve to be measured"
       :url         "https://github.com/tolitius/calip"
       :scm         {:url "https://github.com/tolitius/calip"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
