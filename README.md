# macrowbar
Portable clojure macro utility functions

## Latest version

[![Clojars Project](https://img.shields.io/clojars/v/moxaj/macrowbar.svg)](https://clojars.org/moxaj/mikron)

## Overview

All public functions reside in the `macrowbar.core` namespace.

---

##### `(emit mode & body)`

Macro. In Clojure and self-hosted ClojureScript, it always emits the body. In JVM ClojureScript, it if and only if emits the closure constant `macrowbar.util/DEBUG` is set to `true` and the `mode` argument is equal to `:debug`.

Can be used to strip away all unnecessary compile-time code from JVM ClojureScript js output files.

Example:

```clojure
;; Emitted in all targets
(def n 1)

;; Emitted in Clojure and self-hosted ClojureScript, but not in JVM ClojureScript
(emit :debug-self-hosted ; could actually be any keyword other than `:debug`
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
(ns example.foo
  #?(:cljs (:require-macros example.foo)))

(defmacro macro []
  (if (cljs? &env)
    :cljs
    :clj))

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
(ns example.foo
  #?(:cljs (:require-macros example.foo)))

(def n 3)

(defmacro macro [x]
  `(+ ~@(repeat (eval x) 1)))

(ns example.bar
  (:require [example.foo :as foo]))

(foo/macro `foo/n)
;; => 3
```

---

##### `(with-syms syms & body)`

Utility macro for macros, expected to be used at compile time. Takes a map with optional keys `:gen`, `:bind` and `:eval` - each mapped to a vector of simple symbols - and any number of expressions. For each symbol `x` mapped to:

- `:gen`, it generates a new symbol with the metadata of `x`, and binds it to `x`
- `:bind`: it essentially evaluates `x` at the target runtime (hard to describe, see example)
- `:eval`, it evaluates `x` at the target compile time.

Having any duplicate symbols leads to undefined behaviour (GIGO).

Example for `:gen`:

```clojure
;; ok, but verbose
(defmacro macro []
  (let [a (gensym)
        b (vary-meta (gensym) assoc :tag 'long)]
    ...))

;; better
(defmacro macro []
  (with-syms {:gen [a ^long b]}
    ...))
```

Example for `:bind`:

```clojure
;; bad, potential undesired multiple evaluation
(defmacro macro-1 [x]
  `(+ ~x ~x))

;; better, but a new binding has to be introduced, can't reuse `x`, verbose
(defmacro macro-2 [x]
  (let [x' (gensym)]
    `(let [~x' ~x]
       (+ ~x' ~x'))))

;; better, can reuse `x`, but still verbose
(defmacro macro-3 [x]
  (let [x' (gensym)]
    `(let [~x' ~x]
       ~(let [x x']
          `(+ ~x ~x)))))

;; better
(defmacro macro-4 [x]
  (with-syms {:bind [x]}
    `(+ ~x ~x)))
```

Example for `:eval`:

```clojure
;; ok, but verbose
(defmacro macro [x]
  (let [x (macrowbar.core/eval x)] ;; not `clojure.core/eval`!
    ...))

;; better
(defmacro macro [x]
  (with-syms {:eval [x]}
    ...))
```

## License

Copyright © 2017 Viktor Magyari

Distributed under the Eclipse Public License v1.0.
