## problem: with "trace" exceptions are ignored

```clojure
=> (defn foo [throw?] (if throw? (throw (RuntimeException. "BOOM!")) 42))

=> (foo true)
Execution error at user/foo (REPL:1).
BOOM!

=> (foo false)
42

=> (calip/measure #{#'user/foo} {:trace? true})
wrapping #'user/foo

=> (foo false)
42
{:mulog/event-name #'user/foo,
 :mulog/timestamp 1666235138150,
 :mulog/trace-id #mulog/flake "4lyc0vBg2uYpby4fFLD2Rbe88msAq8so",
 :mulog/root-trace #mulog/flake "4lyc0vBg2uYpby4fFLD2Rbe88msAq8so",
 :mulog/duration 5661,
 :mulog/namespace "calip.core",
 :mulog/outcome :ok}

;; but....

=> (foo true)
Execution error at user/foo (REPL:1).
BOOM!

;; no "µ/trace dice"
```

might be due to a combo of a nested calip + µ/trace macro and a µ/log internal thread
