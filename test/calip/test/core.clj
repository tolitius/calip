(ns calip.test.core
  (:require [calip.core :as c]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [clojure.test :refer :all]
            [com.brunobonacci.mulog :as u]
            [robert.hooke :as hooke]))

;; test functions with fun names
(defn dance [moves]
  (+ moves 21))

(defn boom [x]
  (throw (ex-info "boom goes the dynamite" {:input x})))

(defn brew-coffee [beans temp]
  {:cup "full" :beans beans :temp temp})

(defn pilot-spaceship [destination speed]
  (format "flying to %s at warp %s" destination speed))

(defn fix-flux-capacitor [gigawatts]
  (map (fn [x] (* x 1.21)) (range gigawatts)))

(defn teleport [from to]
  {:from from :to to :distance (* (count from) (count to))})

;; not a function: should never be wrapped
(def the-answer 42)

;; namespace for wildcard tests
(defn jump-high [] "up")
(defn jump-low [] "down")
(defn jump-left [] "left")
(defn jump-right [] "right")

(deftest should-measure-function
  (testing "should wrap and measure a function"
    (let [metrics (atom nil)
          see-it (fn [result]
                   (reset! metrics result)
                   result)]

      ;; measure the dance
      (binding [c/*silent* true]
        (c/measure #{#'calip.test.core/dance} {:report see-it})

        ;; let's dance!
        (is (= 42 (dance 21)))

        ;; check all the dance measurements
        (let [{:keys [took fname args returned]} @metrics]
          ;; did we measure the moves?
          (is (not (nil? took)))
          (is (not (nil? fname)))
          (is (not (nil? args)))
          (is (not (nil? returned)))

          ;; check the dance stats
          (is (= #'calip.test.core/dance fname))
          (is (= '(21) args))
          (is (= 42 returned)))

        ;; cleanup the stage
        (c/uncalip #{#'calip.test.core/dance})))))

(deftest should-measure-function-with-error
  (testing "should wrap and measure a function that throws"
    (let [oops (atom nil)
          catch-error (fn [result]
                        (reset! oops result)
                        result)]

      ;; measure the explosion
      (binding [c/*silent* true]
        (c/measure #{#'calip.test.core/boom} {:report catch-error
                                              :on-error? true})

        ;; kaboom!
        (is (thrown? clojure.lang.ExceptionInfo (boom "dynamite")))

        ;; check all the boom measurements
        (let [{:keys [took fname args error]} @oops]
          ;; did we measure the explosion?
          (is (not (nil? took)))
          (is (not (nil? fname)))
          (is (not (nil? args)))
          (is (not (nil? error)))

          ;; check the boom stats
          (is (= #'calip.test.core/boom fname))
          (is (= '("dynamite") args))
          (is (instance? clojure.lang.ExceptionInfo error))
          (is (= "boom goes the dynamite" (.getMessage error))))

        ;; defuse the bomb
        (c/uncalip #{#'calip.test.core/boom})))))

(deftest should-measure-function-with-custom-reporter
  (testing "should wrap and measure with custom reporter"
    (let [coffee-notes (atom nil)
          custom-report (fn [{:keys [took fname args]}]
                          (reset! coffee-notes
                                  {:brewing-time took
                                   :barista fname
                                   :recipe args}))
          beans "arabica"
          temp 92]

      ;; measure the coffee brewing
      (binding [c/*silent* true]
        (c/measure #{#'calip.test.core/brew-coffee} {:report custom-report})

        ;; brew some coffee
        (is (= {:cup "full" :beans beans :temp temp}
               (brew-coffee beans temp)))

        ;; check our custom coffee notes
        (let [{:keys [brewing-time barista recipe]} @coffee-notes]
          ;; did we track the brewing?
          (is (not (nil? brewing-time)))
          (is (not (nil? barista)))
          (is (not (nil? recipe)))

          ;; check the coffee details
          (is (= #'calip.test.core/brew-coffee barista))
          (is (= (list beans temp) recipe)))

        ;; clean the coffee machine
        (c/uncalip #{#'calip.test.core/brew-coffee})))))

(deftest should-trace-function
  (testing "should wrap and trace a function"
    (binding [c/*silent* true]
      ;; trace the spaceship
      (let [traced (c/trace #{#'calip.test.core/pilot-spaceship})]

        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))
        (is (= (str (first traced)) (str #'calip.test.core/pilot-spaceship)))

        ;; fly to mars without error
        (is (string? (pilot-spaceship "mars" 5)))

        ;; land the ship
        (c/untrace #{#'calip.test.core/pilot-spaceship})))))

(deftest should-trace-function-with-custom-event-name
  (testing "should trace with custom event name"
    (binding [c/*silent* true]
      ;; trace the flux capacitor with custom event
      (let [traced (c/trace #{#'calip.test.core/fix-flux-capacitor}
                           {:event-name ::time-travel})]

        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))
        (is (= (str (first traced)) (str #'calip.test.core/fix-flux-capacitor)))

        ;; fix capacitor without error
        (is (sequential? (fix-flux-capacitor 3)))

        ;; power down
        (c/untrace #{#'calip.test.core/fix-flux-capacitor})))))

(deftest should-trace-function-with-args-formatting
  (testing "should trace with args formatting"
    (binding [c/*silent* true]
      ;; trace with args formatter
      (let [traced (c/trace #{#'calip.test.core/teleport}
                           {:format-args (fn [[from to]]
                                           (str "teleporting from " from " to " to))})]

        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))
        (is (= (str (first traced)) (str #'calip.test.core/teleport)))

        ;; beam me up without error
        (is (map? (teleport "earth" "mars")))

        ;; shut down teleporter
        (c/untrace #{#'calip.test.core/teleport})))))

(deftest should-format-args-as-map
  (testing "should format args as a map"
    (binding [c/*silent* true]
      ;; trace with args as map
      (let [traced (c/trace #{#'calip.test.core/teleport}
                           {:format-args (fn [[from to]]
                                           {:origin from
                                            :destination to})})]

        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))
        (is (= (str (first traced)) (str #'calip.test.core/teleport)))

        ;; teleport without error
        (let [result (teleport "moon" "saturn")]
          (is (map? result))
          (is (= "moon" (:from result)))
          (is (= "saturn" (:to result))))

        ;; power down
        (c/untrace #{#'calip.test.core/teleport})))))

(deftest should-uncalip-remove-measurement
  (testing "should remove measurement wrapper"
    (let [metrics (atom nil)
          see-it (fn [result]
                   (reset! metrics result)
                   result)]

      ;; measure the dance
      (binding [c/*silent* true]
        (c/measure #{#'calip.test.core/dance} {:report see-it})

        ;; measure once
        (dance 21)
        (is (not (nil? @metrics)))
        (reset! metrics nil)

        ;; remove measurement
        (c/uncalip #{#'calip.test.core/dance})

        ;; dance again - should not trigger measurement
        (dance 21)
        (is (nil? @metrics))))))

(deftest should-untrace-remove-tracing
  (testing "should remove trace wrapper"
    (binding [c/*silent* true]
      ;; trace the dance
      (let [traced (c/trace #{#'calip.test.core/dance})]

        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))

        ;; dance once - should work
        (is (= 42 (dance 21)))

        ;; remove trace
        (c/untrace #{#'calip.test.core/dance})

        ;; dance again - should still work
        (is (= 42 (dance 21)))))))

(deftest should-expand-wildcards-in-namespaces
  (testing "should expand wildcards in namespaces"
    (binding [c/*silent* true]
      ;; use namespace wildcard and check return value
      (let [expanded (c/measure #{"#'calip.test.core/*"})]

        ;; verify key functions were found and expanded
        (is (set? expanded))
        (is (> (count expanded) 3))
        (is (some #(= (str %) (str #'calip.test.core/dance)) (map str expanded)))
        (is (some #(= (str %) (str #'calip.test.core/boom)) (map str expanded)))
        (is (some #(= (str %) (str #'calip.test.core/brew-coffee)) (map str expanded)))

        ;; cleanup
        (c/uncalip expanded)))))

(deftest should-expand-prefix-wildcards
  (testing "should expand prefix wildcards"
    (binding [c/*silent* true]
      ;; use prefix wildcard and check return value
      (let [expanded (c/measure #{"#'calip.test.core/jump-*"})]

        ;; verify only jump functions were expanded
        (is (set? expanded))
        (is (<= 4 (count expanded)))
        (is (some #(= (str %) (str #'calip.test.core/jump-high)) (map str expanded)))
        (is (some #(= (str %) (str #'calip.test.core/jump-low)) (map str expanded)))
        (is (some #(= (str %) (str #'calip.test.core/jump-left)) (map str expanded)))
        (is (some #(= (str %) (str #'calip.test.core/jump-right)) (map str expanded)))
        (is (not (some #(= (str %) (str #'calip.test.core/dance)) (map str expanded))))

        ;; land safely
        (c/uncalip expanded)))))

(deftest should-handle-multiple-functions
  (testing "should handle multiple functions at once"
    (let [metrics (atom {})
          multi-report (fn [result]
                         (swap! metrics assoc (:fname result) result)
                         result)]

      ;; measure multiple functions
      (binding [c/*silent* true]
        (c/measure #{#'calip.test.core/dance
                     #'calip.test.core/pilot-spaceship}
                   {:report multi-report})

        ;; call both functions
        (dance 21)
        (pilot-spaceship "pluto" 9)

        ;; verify both were measured
        (is (= 2 (count @metrics)))
        (is (contains? @metrics #'calip.test.core/dance))
        (is (contains? @metrics #'calip.test.core/pilot-spaceship))

        ;; cleanup
        (c/uncalip #{#'calip.test.core/dance
                     #'calip.test.core/pilot-spaceship})))))

(deftest should-handle-silent-mode
  (testing "should respect silent mode"
    (let [output (atom [])
          orig-println println]

      ;; capture println output
      (with-redefs [println (fn [& args]
                              (swap! output conj (apply str args))
                              (apply orig-println args))]

        ;; with silent mode
        (binding [c/*silent* true]
          (c/measure #{#'calip.test.core/dance})
          (is (empty? @output)))

        ;; reset captured output
        (reset! output [])

        ;; without silent mode (will print)
        (binding [c/*silent* false]
          (try
            (c/measure #{#'calip.test.core/dance})
            (is (some #(s/includes? % "wrapping") @output))
            (finally
              (c/uncalip #{#'calip.test.core/dance}))))))))

(deftest should-default-format-handle-success
  (testing "should format successful results correctly"
    (let [result {:fname "#'test/fn"
                  :took 12345
                  :args '(1 2 3)
                  :returned "success"}
          formatted (c/default-format result)]

      ;; check format contains expected parts
      (is (s/includes? formatted "#'test/fn"))
      (is (s/includes? formatted "args: (1 2 3)"))
      (is (s/includes? formatted "took: 12,345"))
      (is (s/includes? formatted "returned: success")))))

(deftest should-default-format-handle-error
  (testing "should format error results correctly"
    (let [ex (ex-info "test error" {})
          result {:fname "#'test/fn"
                  :took 12345
                  :args '(1 2 3)
                  :error ex}
          formatted (c/default-format result)]

      ;; check format contains expected parts
      (is (s/includes? formatted "#'test/fn"))
      (is (s/includes? formatted "args: (1 2 3)"))
      (is (s/includes? formatted "took: 12,345"))
      (is (s/includes? formatted "error: clojure.lang.ExceptionInfo")))))

(deftest should-respect-global-context
  (testing "should respect µ/log global context"
    (binding [c/*silent* true]
      ;; set global context
      (u/set-global-context! {:app "test-app" :version "1.0"})

      ;; trace with global context
      (let [traced (c/trace #{#'calip.test.core/dance})]
        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))

        ;; dance! (should include global context)
        (is (= 42 (dance 21)))

        ;; cleanup
        (c/untrace #{#'calip.test.core/dance})
        (u/set-global-context! {})))))

(deftest should-respect-local-context
  (testing "should respect µ/log local context"
    (binding [c/*silent* true]
      ;; trace with local context
      (let [traced (c/trace #{#'calip.test.core/dance})]
        ;; verify function was traced
        (is (set? traced))
        (is (= 1 (count traced)))

        ;; dance with local context (should include local context)
        (u/with-context {:mood "funky" :energy "high"}
          (is (= 42 (dance 21))))

        ;; cleanup
        (c/untrace #{#'calip.test.core/dance})))))

(deftest should-wrap-in-custom-advice
  (testing "should wrap a function in a custom advice that can change args and results"
    (binding [c/*silent* true]
      (let [seen (atom nil)]
        (c/wrap #{#'calip.test.core/dance}
                (fn [fname f & args]
                  (reset! seen fname)
                  (inc (apply f (map inc args)))))

        ;; (21 + 1) + 21 + 1
        (is (= 44 (dance 21)))
        (is (= #'calip.test.core/dance @seen))
        (is (= {#'calip.test.core/dance #{:wrap}}
               (select-keys (c/wrapped) [#'calip.test.core/dance])))

        (c/unwrap #{#'calip.test.core/dance})
        (is (= 42 (dance 21)))
        (is (not (contains? (c/wrapped) #'calip.test.core/dance)))))))

(deftest should-short-circuit-in-custom-advice
  (testing "advice decides whether to call a function"
    (binding [c/*silent* true]
      (c/wrap #{#'calip.test.core/boom}
              (fn [_ f & args]
                (try
                  (apply f args)
                  (catch Exception _ :defused))))
      (is (= :defused (boom "dynamite")))
      (c/unwrap #{#'calip.test.core/boom})
      (is (thrown? clojure.lang.ExceptionInfo (boom "dynamite"))))))

(deftest should-stack-wrappers
  (testing "should stack measure, trace and custom wrappers on the same function"
    (binding [c/*silent* true]
      (let [metrics (atom nil)
            calls (atom [])]
        (c/measure #{#'calip.test.core/dance} {:report #(reset! metrics %)})
        (c/trace #{#'calip.test.core/dance})
        (c/wrap #{#'calip.test.core/dance}
                (fn [_ f & args]
                  (swap! calls conj :wrap)
                  (apply f args)))
        (c/wrap #{#'calip.test.core/dance}
                (fn [_ f & args]
                  (swap! calls conj :retry)
                  (apply f args))
                {:id :retry})

        (is (= #{:measure :trace :wrap :retry}
               (get (c/wrapped) #'calip.test.core/dance)))
        (is (= 42 (dance 21)))
        (is (= 42 (:returned @metrics)))
        ;; the last added wrapper is the outermost one
        (is (= [:retry :wrap] @calls))

        ;; remove just one
        (reset! calls [])
        (c/unwrap #{#'calip.test.core/dance} {:id :retry})
        (is (= #{:measure :trace :wrap}
               (get (c/wrapped) #'calip.test.core/dance)))
        (dance 21)
        (is (= [:wrap] @calls))

        ;; untrace only removes the trace
        (c/untrace #{#'calip.test.core/dance})
        (is (= #{:measure :wrap}
               (get (c/wrapped) #'calip.test.core/dance)))

        ;; uncalip removes the rest
        (c/uncalip #{#'calip.test.core/dance})
        (reset! calls [])
        (reset! metrics nil)
        (is (= 42 (dance 21)))
        (is (empty? @calls))
        (is (nil? @metrics))
        (is (not (contains? (c/wrapped) #'calip.test.core/dance)))))))

(deftest should-keep-non-calip-hooks
  (testing "uncalip should only remove calip wrappers"
    (binding [c/*silent* true]
      (hooke/add-hook #'calip.test.core/dance ::not-calip
                      (fn [f & args] (* 2 (apply f args))))
      (c/wrap #{#'calip.test.core/dance}
              (fn [_ f & args] (inc (apply f args))))
      (is (= 85 (dance 21)))
      (c/uncalip #{#'calip.test.core/dance})
      (is (= 84 (dance 21)))
      (hooke/remove-hook #'calip.test.core/dance ::not-calip)
      (is (= 42 (dance 21))))))

(deftest should-skip-non-functions
  (testing "wildcards should only wrap functions"
    (binding [c/*silent* true]
      (let [wrapped (c/wrap #{"#'calip.test.core/*"}
                            (fn [_ f & args] (apply f args)))]
        (is (contains? wrapped #'calip.test.core/dance))
        (is (not (contains? wrapped #'calip.test.core/the-answer)))
        (is (= 42 the-answer))
        (c/uncalip wrapped)))))

(deftest should-uncalip-all-wrapped
  (testing "(uncalip (wrapped)) should remove all the wrappers"
    (binding [c/*silent* true]
      (c/measure #{#'calip.test.core/dance #'calip.test.core/brew-coffee})
      (c/trace #{#'calip.test.core/dance})
      (c/uncalip (c/wrapped))
      (is (empty? (c/wrapped))))))
