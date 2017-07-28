# calip(er)

measuring function performance on demand _without_ a need to alter the code

[![Clojars Project](http://clojars.org/tolitius/calip/latest-version.svg)](http://clojars.org/tolitius/calip)

## All functions deserve to be measured

calip measures function performance on demand without a need to alter the code.

It does so by adding an AOP around advice (i.e. a weaved timer function wrapper) with [robert hooke](https://github.com/technomancy/robert-hooke)

It comes really handy at development time, as well as deployed applications,
when you know something is slowing it down, and need an _on demand_ performance metrics.
In which case you can just connect to a deployed application via an `nREPL`, and add measurements to _any_ "functional suspect".

## Show me

Let's pretend we have an app with two functional suspects:

```clojure
=> (defn rsum [n] (reduce + (range n)))
#'boot.user/rsum
=> (defn rmult [n] (reduce *' (range 1 n)))
#'boot.user/rmult

=> (rsum 10)
45
=> (rmult 10)
362880
```

Now let's measure them:

```clojure
=> (require '[calip.core :as calip])

=> (calip/measure #{#'boot.user/rsum
                    #'boot.user/rmult})

=> (rsum 10)
"#'boot.user/rsum" (10) took: 31,506 nanos
45

=> (rmult 10)
"#'boot.user/rmult" (10) took: 17,334 nanos
362880
```

`(10)` here shows the arguments to a function that is measured, or "an" argument in this case.

these measurements can be removed of course:

```clojure
=> (calip/uncalip #{#'boot.user/rsum})

=> (rsum 10)
45
=> (rmult 10)
"#'boot.user/rmult" (10) took: 16,485 nanos
362880
```

or remove it from both:

```clojure
=> (calip/uncalip #{#'boot.user/rsum #'boot.user/rmult})

=> (rsum 10)
45
=> (rmult 10)
362880
```

## Reporting

By default calip will use `println` and a "default format" as shown above to report metrics, but it is pluggable.
You can pass a report function to `calip/measure`. calip would pass a map to this function with:

```clojure
{:took took     ;; time this function took to execute in nanoseconds
 :fname fname   ;; function name with a namespace
 :args args}    ;; arguments that were passed to this function
```

Quite a useful scenario to use calip on the live application that writes logs. We can tap into that:

```clojure
=> (require '[clojure.tools.logging :as log])

=> (calip/measure #{#'boot.user/rsum #'boot.user/rmult} {:report #(log/info (calip/default-format %))})

=> (rsum 10)
13:42:04.048 [nREPL-worker-24] INFO  boot.user - "#'boot.user/rsum" (10) took: 13,091 nanos
45
=> (rmult 10)
13:42:07.687 [nREPL-worker-24] INFO  boot.user - "#'boot.user/rmult" (10) took: 16,535 nanos
362880
```

notice we used `(calip/default-format %)` to format that `{:took .., :fname .., :args ..}` map, but you can of course customize it:

```clojure
=> (defn create-life [{:keys [galaxy planet]}] "creating life...")
#'boot.user/create-life
=>

=> (create-life {:galaxy "pegasus" :planet "athos"})
"creating life..."

=> (calip/measure #{#'boot.user/create-life} {:report (fn [{:keys [took fname]}]
                                                        (log/info fname "took" took "ns"))})

=> (create-life {:galaxy "pegasus" :planet "athos"})
13:54:20.334 [nREPL-worker-25] INFO  boot.user - #'boot.user/create-life took 2681 ns
"creating life..."
```

or with args:

```clojure
=> (calip/measure #{#'boot.user/create-life} {:report (fn [{:keys [took fname args]}]
                                                        (log/info fname "with args:" args "took" took "ns"))})

=> (create-life {:galaxy "pegasus" :planet "athos"})
13:54:56.884 [nREPL-worker-26] INFO  boot.user - #'boot.user/create-life with args: ({:galaxy pegasus, :planet athos}) took 2739 ns
"creating life..."
```

## License

Copyright © 2017 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
