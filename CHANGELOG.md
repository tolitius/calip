## 0.1.15
###### Thu Oct 01 17:08:44 2026 -0400

### wrap them in anything

* add `calip/wrap`: wrap functions in _any_ function (a.k.a. "around advice"), without changing the code

```clojure
=> (calip/wrap #{#'user/rsum}
               (fn [fname f & args]
                 (println "calling" fname "with" args)
                 (apply f args)))
wrapping #'user/rsum in :wrap

=> (rsum 10)
calling #'user/rsum with (10)
45
```

  an advice takes a function name (var), the function itself and its arguments: `(fn [fname f & args] ...)`<br/>
  it decides whether to call the function, how many times, with which arguments and what to return: retry, short circuit, redact, etc.

* add `calip/unwrap`: removes calip wrappers from functions: all of them, or just one by `{:id ...}`
* `measure` and `trace` are now built on top of `wrap` (they are just two built in advices)

### stacking wrappers

* each wrapper has an id: `:measure`, `:trace`, `:wrap` (default for `calip/wrap`) or any custom one via `{:id ...}`
* wrappers with different ids stack: a function can now be measured, traced and retried at the same time

```clojure
=> (calip/measure #{#'user/connect})
=> (calip/trace #{#'user/connect})
=> (calip/wrap #{#'user/connect} retry {:id :retry})
```

  the last added wrapper is the outermost one. wrapping again with the same id replaces that wrapper.

* `measure` and `trace` take an optional `:id` as well, i.e. to measure the same function with two different reporters

### fixes

* `uncalip` no longer removes _all_ the hooks from a function: hooks that are not added by calip (i.e. directly via robert.hooke) are left alone
* `trace` after `measure` (or vice versa) no longer silently replaces the previous wrapper
* wildcards (i.e. `"#'user/*"`) only wrap functions: values, macros and multimethods are skipped
* `(wrapped)` only contains resolved vars (it used to contain a mix of vars and strings, depending on what was passed in)
* `calip`, `on-error`, `uncalip` and `untrace` docstrings are now real docstrings

### heads up :warning:

a few behavior changes to be aware of:

* `(calip/wrapped)` returns a map of functions to their wrapper ids instead of a set of functions

```clojure
=> (calip/wrapped)
{#'user/connect #{:measure :trace :retry}
 #'user/rsum #{:wrap}}
```

  `(calip/uncalip (calip/wrapped))` still works and removes all the wrappers

* `untrace` only removes `:trace` wrappers (it used to remove all of them), use `uncalip` / `unwrap` to remove all
* wrap / unwrap output is slightly different:

```clojure
wrapping #'user/rsum in :trace                 ;; was: wrapping #'user/rsum in µ/trace
remove :trace wrapper from #'user/rsum         ;; was: remove a wrapper from #'user/rsum
skipping #'user/+version+ since it is not a function
```

  it can still be silenced with `calip/*silent*`
