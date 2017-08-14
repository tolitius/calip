# calip(er)

measuring function performance on demand _without_ a need to alter the code

[![Clojars Project](http://clojars.org/tolitius/calip/latest-version.svg)](http://clojars.org/tolitius/calip)

## All functions deserve to be measured

calip measures function performance on demand, or in case of an error, without a need to alter the code.

It does so by adding an AOP around advice (i.e. a weaved timer function wrapper) with [robert hooke](https://github.com/technomancy/robert-hooke)

It comes really handy at development time, as well as for deployed applications:

* when you know something is slowing it down and need an _on demand_ performance metrics with runtime arguments
* when you need to see the actual runtime arguments in case of an error

In which case you can just connect to a deployed application via an `nREPL`, and add measurements to _any_ "functional suspect".

## Performance on demand

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

## Taming runtime errors

Most of the time in case of a runtime error/exception JVM reports an array of stack trace elements, each representing one stack frame. This array is also known as a stacktrace. While it is immensely useful for tracking down the root cause of an error it falls short to provide a _state_ snapshot at the time an error occurred: i.e. "what were the arguments passed into a function which could have caused this error in the first place?"

`calip` helps tracking down these runtime arguments by setting an "`on-error?`" flag on a measurement:

```clojure
=> (defn connect [{:keys [host port]}]
     (java.net.Socket. host port))
```
```clojure
=> (connect {:host "my-good-host.com" :port 7889})
#object[java.net.Socket 0x59cdc19b "Socket[addr=my-good-host.com/10.X.X.23,port=7889,localport=62446]"]

=> (connect {:host "8.8.8.8" :port 1025})

java.net.ConnectException: Operation timed out

=> (connect {:host "127.0.0.1" :port 1025})

java.net.ConnectException: Connection refused
```

In case of an error JVM reports an exception but there is no visual on what the arguments were that caused this exception.

Let's fix it without a code change / on a running application:

```clojure
=> (calip/measure #{#'boot.user/connect} {:on-error? true})
```

we can still normally connect without any extra logging / metrics:

```clojure
=> (connect {:host "my-good-host.com" :port 7889})
#object[java.net.Socket 0x59cdc19b "Socket[addr=my-good-host.com/10.X.X.23,port=7889,localport=62446]"]
```

but in case of an error, in addition to the time a function took, `calip` will report the actual runtime args that led to this error:

```clojure
=> (connect {:host "8.8.8.8" :port 1025})
"#'boot.user/connect" ({:host "8.8.8.8", :port 1025}) took: 75,316,432,430 nanos

java.net.ConnectException: Operation timed out
```

```clojure
=> (connect {:host "127.0.0.1" :port 22})
"#'boot.user/connect" ({:host "127.0.0.1", :port 22}) took: 341,806 nanos

java.net.ConnectException: Connection refused
```

> `on-error?` flag can be combined with a custom `:report` function that is documented in the next section.

## Reporting

By default calip will use `println` and a "default format" as shown above to report metrics, but it is pluggable.
You can pass a report function to `calip/measure`. calip would pass a map to this function with:

```clojure
{:took took     ;; time this function took to execute in nanoseconds
 :fname fname   ;; function name with a namespace
 :args args}    ;; arguments that were passed to this function
```

Quite a useful scenario is to use calip to measure parts of the application that writes logs. We can tap into that:

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
