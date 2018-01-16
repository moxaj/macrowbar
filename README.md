# macrowbar
Portable clojure macro utility functions

## Latest version

[![Clojars Project](https://img.shields.io/clojars/v/moxaj/macrowbar.svg)](https://clojars.org/moxaj/mikron)

## Overview

All public functions reside in the `macrowbar.core` namespace.

---

##### `(emit mode & body)`

Macro. In Clojure and self-hosted ClojureScript, it always emits the body. In JVM ClojureScript, it only emits if the closure constant `macrowbar.util/DEBUG` is set to `true` and the `mode` argument is equal to `:debug`.

Can be used to strip away all unnecessary compile-time code from JVM ClojureScript js output files.

Example:

```clojure
;; Emitted in all targets
(def n 1)

;; Emitted in Clojure and self-hosted ClojureScript, but not in JVM ClojureScript
(emit :debug-self-hosted
  (def n 1))

;; Emitted in Clojure and self-hosted ClojureScript, also in JVM ClojureScript if and only if DEBUG is set
(emit :debug
  (def n 1))
```

---

##### `(cljs? env)`

This function expects the hidden `&env` argument of a macro as the single argument, and returns `true` if that macro is being compiled as a ClojureScript macro (i.e. self-hosted).

Example:

```clojure
;; src/example/foo.cljc
(ns example.foo
  #?(:cljs (:require-macros example.foo)))

(defmacro macro []
  (if (cljs? &env)
    :cljs
    :clj))

;; src/example/bar.cljc
(ns example.bar
  (:require [example.foo :as foo]))

(println (foo/macro))
;; => prints :clj in Clojure and JVM ClojureScript, :cljs in self-hosted ClojureScript
```

---

##### `(eval expr)`

Evaluates the expression. This function is expected to be used at compile-time, and has mostly the same semantics as in Clojure: local bindings in its lexical scope are not visible, and in self-hosted ClojureScript, functions / vars used should be defined in a separate compilation stage (i.e. in a namespace other than the one currently being compiled).

Can be used to evaluate macro arguments (for whatever reason).

Example:

```clojure
;; src/example/foo.cljc
(ns example.foo
  #?(:cljs (:require-macros example.foo)))

(def n 3)

(defmacro macro [x]
  `(+ ~@(repeat (eval x) 1)))

;; src/example/bar.cljc
(ns example.bar
  (:require [example.foo :as foo]))

(foo/macro `foo/n)
;; => 3
```

---

##### `(with-gensyms symbols & body)`

Takes a vector of symbols and binds each to a generated symbol (via `gensym`), then executes each expression in `body`.

Example:

```clojure
;; instead of this ...
(defmacro macro []
  (let [a (gensym)
        b (gensym)
        c (vary-meta (gensym) assoc :tag 'long)]
    ...))

;; ... you can do this
(defmacro macro []
  (with-gensyms [a b ^long c]
    ...))
```

---

##### `(with-evaluated symbols & body)`

Takes a vector of symbols and force evaluates each, then executes body.

Can be used to prevent accidental multiple evaluation of side-effectful arguments.

Example:

```clojure
;; src/example/foo.cljc
(ns example.foo
  #?(:cljs (:require-macros example.foo)))

;; instead of this ...
(defmacro macro-1 [x]
  (let [x' (gensym)]
    `(let [~x' ~x]
       ~(let [x x']
          `(+ ~x ~x)))))

;; ... you can do this
(defmacro macro-2 [x]
  (with-evaluated [x]
    `(+ ~x ~x)))

;; src/example/bar.cljc
(ns example.bar
  (:require [example.foo :as foo]))

;; ლ( ¤ 益 ¤ )┐
(foo/macro-1 (do (println "cake") 10))
;; "cake"
;; "cake"
;; => 20

;; ʕ༼◕  ౪  ◕✿༽ʔ
(foo/macro-2 (do (println "cake") 10))
;; "cake"
;; => 20
```

---

##### `(macro-context params & body)`

This macro combines the previous two (`with-gensyms` and `with-evaluated`) into one. `params` should be a map with optional keys `:gen-syms` and `:eval-syms`, each mapped to a vector of symbols.

Example:

```clojure
;; instead of this ...
(defmacro macro-1 [x]
  (let [x' (gensym)
        y  (gensym)]
    `(let [~y  :cake
           ~x' ~x]
       ~(let [x x']
          ...))))

;; ... you can do this
(defmacro macro-2 [x]
  (macro-context {:gen-syms [y] :eval-syms [x]}
    ...))
```

## License

Copyright © 2017 Viktor Magyari

Distributed under the Eclipse Public License v1.0.
