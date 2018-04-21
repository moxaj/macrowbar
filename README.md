# macrowbar (WORK IN PROGRESS)
A set of portable macro utility functions.

## Latest version

[![Clojars Project](https://img.shields.io/clojars/v/moxaj/macrowbar.svg)](https://clojars.org/moxaj/mikron)

## Overview

All public functions reside in the `macrowbar.core` namespace. In the scope of this document, consider this namespace implicitly loaded and aliased to `macrowbar`.

Some examples below define and then immediately call macros; note that in self-hosted ClojureScript, this is only possible if the macro is defined in a different compilation stage (ie. namespace). Futhermore, if a macro were to resolve a symbol at compile time (via `eval` or `partial-eval`), then that symbol has to be in a different compilation stage than the one in which the macro was invoked. To illustrate:

```clojure
;; src/foo.cljc
(ns foo
  #?(:cljs [:require-macros foo]))

(defmacro macro [x]
  (macrowbar/eval x))

;; src/bar.cljc
(ns bar
  (:require [foo]))

(def y 10)

;; Will not compile in self-hosted ClojureScript!
(foo/macro y)
```

---

##### `(macrowbar/emit mode & body)`

Macro. In Clojure and self-hosted ClojureScript, it always emits the body. In JVM ClojureScript, it if and only if emits the closure constant `macrowbar.util/DEBUG` is set to `true` and the `mode` argument is equal to `:debug`.

Can be used to strip away all unnecessary compile-time code from JVM ClojureScript js output files.

Example:

```clojure
;; Emitted in all targets
(def n 1)

;; Emitted in Clojure and self-hosted ClojureScript, but not in JVM ClojureScript
(macrowbar/emit :debug-self-hosted ; could actually be any keyword other than `:debug`
  (def n 1))

;; Emitted in Clojure and self-hosted ClojureScript, also in JVM ClojureScript if and only if DEBUG is set
(macrowbar/emit :debug
  (def n 1))
```

---

##### `(macrowbar/cljs? env)`

This function expects the hidden `&env` argument of a macro as the single argument, and returns `true` if that macro is being compiled as a ClojureScript macro (i.e. self-hosted).

Example:

```clojure
(defmacro macro []
  (if (macrowbar/cljs? &env)
    :cljs
    :clj))

(println (macro))
;; => prints :clj in Clojure and JVM ClojureScript, :cljs in self-hosted ClojureScript
```

---

##### `(macrowbar/partial-eval expr)`

Prewalks the given expression, and evaluates each subvalue marked with an `'eval` tag.

Example:

```clojure
(defmacro macro [x]
  `(+ ~@(repeat (macrowbar/partial-eval x) 1)))

(macroexpand '(macro ^eval (+ 1 2)))
;; => (clojure.core/+ 1 1 1)

(macro ^eval (+ 1 2))
;; => 3
```

---

##### `(macrowbar/with-syms syms & body)`

Utility macro for macros, expected to be used at compile time. Takes a map with optional keys `:gen`, `:bind` and `:eval` - each mapped to a vector of simple symbols - and any number of expressions. For each symbol `x` mapped to:

- `:gen`, it generates a new symbol with the metadata of `x`, and binds it to `x`
- `:bind`: it essentially evaluates `x` at the target runtime (hard to describe, see example)
- `:eval`, it evaluates parts of `x` (those marked with `^eval`) at the target compile time.

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
  (macrowbar/with-syms {:gen [a ^long b]}
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
  (macrowbar/with-syms {:bind [x]}
    `(+ ~x ~x)))
```

Example for `:eval`:

```clojure
;; ok, but verbose
(defmacro macro [x]
  (let [x (macrowbar.core/partial-eval x)]
    ...))

;; better
(defmacro macro [x]
  (macrowbar/with-syms {:eval [x]}
    ...))
```

## License

Copyright © 2017 Viktor Magyari

Distributed under the Eclipse Public License v1.0.
