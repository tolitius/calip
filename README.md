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
"#'boot.user/rsum" took: 31,506 nanos
45

=> (rmult 10)
"#'boot.user/rmult" took: 17,334 nanos
362880
```

these measurements can be removed of course:

```clojure
=> (calip/uncalip #{#'boot.user/rsum})

=> (rsum 10)
45
=> (rmult 10)
"#'boot.user/rmult" took: 16,485 nanos
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

By default calip will use `println` to report metrics, but it is pluggable:

```clojure
=> (require '[clojure.tools.logging :as log])

=> (calip/measure #{#'boot.user/rsum #'boot.user/rmult}
                  {:report #(log/info %)})

=> (rsum 10)
INFO  boot.user - "#'boot.user/rsum" took: 14,793 nanos
45

=> (rmult 10)
INFO  boot.user - "#'boot.user/rmult" took: 16,279 nanos
362880
```

* _`log/info` is a macro, hence in order to compose we did `#(log/info %)`_

## License

Copyright © 2017 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
