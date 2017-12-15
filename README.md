# calip(er)

measuring and debugging functions on demand _**without**_ a need to alter the code

[![Clojars Project](http://clojars.org/tolitius/calip/latest-version.svg)](http://clojars.org/tolitius/calip)

- [What does it do?](#what-does-it-do)
- [Performance on demand](#performance-on-demand)
- [Taming runtime errors](#taming-runtime-errors)
  - [Measuring on error](#measuring-on-error)
- [Reporting](#reporting)
  - [Custom reporting](#custom-reporting)
  - [Custom reports on errors](custom-reports-on-errors)

## What does it do?

calip _measures_ and _debugs_ functions on demand, or in case of an error, _**without**_ a need to alter the code.

It does so by adding an AOP around advice (i.e. a weaved timer function wrapper) with [robert hooke](https://github.com/technomancy/robert-hooke)

It comes really handy at development time, as well as for deployed applications:

* when you need _on demand_ performance metrics with runtime arguments
* when you need to see the actual runtime function arguments in case of an error
* when you need to see the actual runtime function arguments as the program is running

In which case you can just connect to a deployed application via an `nREPL`, and add measurements, handlers, logs to _any_ "functional suspect".

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
"#'boot.user/rsum" args: (10) | took: 13,969 nanos | returned: 45
45

=> (rmult 10)
"#'boot.user/rmult" args: (10) | took: 16,402 nanos | returned: 362880
362880
```

`(10)` here shows the runtime arguments to a function that is measured, or "an" argument in this case.

these measurements can be removed of course:

```clojure
=> (calip/uncalip #{#'boot.user/rsum})

=> (rsum 10)
45
=> (rmult 10)
"#'boot.user/rmult" args: (10) | took: 17,479 nanos | returned: 362880
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

Most of the time, in case of a runtime error/exception, JVM reports an array of stack trace elements, each representing one stack frame. This array is also known as a stacktrace.

While it is immensely useful for tracking down an error scope (i.e. _where_ it happened), it falls short to provide a _state_ snapshot at the time an error occurred: i.e. "what were the arguments passed to a function _at the time_ the error occurred?"

`calip` helps tracking down these runtime arguments by setting an "`:on-error?`" flag on a measurement.

### Measuring on error

As an example let's take a function that creates a socket (i.e. connects) to external systems:

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

In case of an error JVM reports an exception but there is no visual on what the arguments were at the time of this exception.

Let's fix it _without a code change_ / on a running application:

```clojure
=> (calip/measure #{#'boot.user/connect} {:on-error? true})
```

we can still normally connect without any extra logging / metrics:

```clojure
=> (connect {:host "my-good-host.com" :port 7889})
#object[java.net.Socket 0x3bc7a27c "Socket[addr=my-good-host.com/10.X.X.23,port=7889,localport=62446]"]
```

but in case of an error, in addition to the time a function took, `calip` will report the actual runtime args that led to this error:

```clojure
=> (connect {:host "8.8.8.8" :port 1025})
"#'boot.user/connect" args: ({:host "8.8.8.8", :port 1025}) | took: 75,696,573,373 nanos | error: java.net.ConnectException: Operation timed out

java.net.ConnectException: Operation timed out
```

```clojure
=> (connect {:host "127.0.0.1" :port 22})
"#'boot.user/connect" args: ({:host "127.0.0.1", :port 22}) | took: 309,753 nanos | error: java.net.ConnectException: Connection refused

java.net.ConnectException: Connection refused
```

> _`:on-error?` flag can be combined with a custom `:report` function that is documented in the next section_

## Reporting

By default calip will use `println` and a "default format" as shown above to report metrics, but it is pluggable.
You can pass a report function to `calip/measure`. calip would pass a map to this function with:

```clojure
{:took took           ;; time this function took to execute in nanoseconds
 :fname fname         ;; function name with a namespace
 :args args           ;; arguments that were passed to this function
 :returned / :error}  ;; a :returned value or an :error [depending on whether the :on-error? flag is set]
```

Quite a useful scenario is to use calip to measure or debug parts of the application that writes logs. We can tap into that:

```clojure
=> (require '[clojure.tools.logging :as log])

=> (calip/measure #{#'boot.user/rsum #'boot.user/rmult} {:report #(log/info (calip/default-format %))})

=> (rsum 10)
13:42:04.048 [nREPL-worker-24] INFO  boot.user - "#'boot.user/rsum" args: (10) | took: 14,928 nanos | returned: 45
45
=> (rmult 10)
13:42:07.687 [nREPL-worker-24] INFO  boot.user - "#'boot.user/rmult" args: (10) | took: 16,280 nanos | returned: 362880
362880
```

notice we used `(calip/default-format %)` to format that `{:took .., :fname .., :args .., :returned}` map, but you can of course customize it.

### Custom reporting

```clojure
=> (defn create-life [{:keys [galaxy planet]}] "creating life...")
#'boot.user/create-life
=>

=> (create-life {:galaxy "pegasus" :planet "athos"})
"creating life..."

=> (calip/measure #{#'boot.user/create-life} {:report (fn [{:keys [took fname]}]
                                                        (log/info fname "took" took "ns"))})

=> (create-life {:galaxy "pegasus" :planet "athos"})
13:54:20.334 [nREPL-worker-25] INFO  boot.user - #'boot.user/create-life took 2637 ns
"creating life..."
```

or with args and return values:

```clojure
=> (calip/measure #{#'boot.user/create-life} {:report (fn [{:keys [took fname args returned]}]
                                                        (log/info "\n|>" fname "\n|> with args:" args "\n|> took:" took "ns \n|> return value:" returned))})

=> (create-life {:galaxy "pegasus" :planet "athos"})
INFO  boot.user -
|> #'boot.user/create-life
|> with args: ({:galaxy pegasus, :planet athos})
|> took: 2911 ns
|> return value: creating life...

"creating life..."
```

### Custom reports on errors

A custom `:report` function can be combined with an `:on-error?` flag:

```clojure
boot.user=> (calip/measure #{#'boot.user/connect}
                           {:on-error? true
                            :report #(log/info (calip/default-format %))})

boot.user=> (connect {:host "127.0.0.1" :port 22})
INFO  boot.user - "#'boot.user/connect" args: ({:host "127.0.0.1", :port 22}) | took: 339,019 nanos | error: java.net.ConnectException: Connection refused
```

or

```clojure
boot.user=> (calip/measure #{#'boot.user/rsum}
                           {:report #(log/info (calip/default-format %))
                            :on-error? true})
                            
boot.user=> (rsum "oops")
INFO  boot.user - "#'boot.user/rsum" args: ("oops") | took: 87,268 nanos | error: java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Number
```



## License

Copyright © 2017 tolitius

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
