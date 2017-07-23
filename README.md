# macrowbar
Portable clojure macro utility functions

## Latest version

[![Clojars Project](https://img.shields.io/clojars/v/moxaj/macrowbar.svg)](https://clojars.org/moxaj/mikron)

## Overview

All public functions reside in the `macrowbar.core` namespace.

---

##### `(compile-time & body)`

Macro. Takes any number of expressions, and emits them depending on the environment:

 - when in Clojure or self-hosted ClojureScript, it always emits
 - when in JVM ClojureScripts, in emits only if the namespace being currently compiled is a Clojure macro namespace

Can be used to strip away all unnecessary compile-time code from JVM ClojureScript js output files.

Example:

```clojure
(compile-time
  (defn foo [x]
    (inc x))

  (defmacro bar [x]
    (foo x)))
```

---

##### `(cljs? env)`

This function expects a macros hidden `&env` parameter as the single argument, and returns `true` if the macro is being compiled as a ClojureScript macro (i.e. self-hosted).

Example:

```clojure
(defmacro foo []
  (if (cljs? &env)
    :cljs
    :clj))
```

---

##### `(eval expr)`

Evaluates the expression. This function is expected to be used at compile-time, and has mostly the same semantics as in Clojure: closed over local bindings are not visible, and in self-hosted ClojureScript, functions / vars used should be defined in a separate compilation stage (i.e. in a namespace other than the one currently being compiled).

Can be used to evaluate macro arguments which could be compile-time constants.

Example:

```clojure
(defmacro foo [x]
  `(+ 1 ~(eval x)))

(foo (bar/baz))
```

---

##### `(with-gensyms symbols & body)`

Takes a vector of symbols and binds each to a generated symbol (via `gensym`), then executes each expression in `body`.

Example:

```clojure
;; instead of this
(defmacro foo []
  (let [a (gensym)
        b (gensym)
        c (vary-meta (gensym) assoc :tag 'long)] ;; preserves meta!
    ...))

;; you can do this
(defmacro foo []
  (with-gensyms [a b ^long c]
    ...))
```

---

##### `(with-evaluated symbols & body)`

Takes a vector of symbols and force evaluates each, then executes body.

Can be used to prevent accidental multiple evaluation of side-effectful parameters.

Example:

```clojure
;; instead of this
(defmacro foo [x y]
  (let [x' (gensym)]
    `(let [~x' ~x]
       ;; at this point, you either keep using x' (inconvenient), or do the following
       ~(let [x x']
          ...))))

;; you can do this
(defmacro foo [x y]
  (with-evaluated [x]
    ...)) ;; you can keep using x

;; to prevent this
(defmacro bar [side-effectful-parameter huge-chunk-of-code]
  `(+ ~side-effectful-parameter
      ~side-effectful-parameter
      ~huge-chunk-of-code
      ~huge-chunk-of-code))

;; prints "nay" twice, the expanded code includes [1 2 3 ... 100]
;; twice (results in exploded code size, painful in ClojureScript)
(bar (do (println "nay") 1)
     [1 2 3 ... 100]])
```

---

##### `(macro-context params & body)`

This macro combines the previous two (`with-gensyms` and `with-evaluated`) into one. `params` should be a map with optional keys `:gen-syms` and `:eval-syms`, each mapped to a vector of symbols.

Example:

```clojure
;; instead of this
(defmacro foo [x y]
  (let [z  (gensym)
        y' (gensym)]
    `(let [~z  :cake
           ~y' ~y]
       ~(let [y y']
          ...))))

;; you can do this
(defmacro foo [x y]
  (macro-context {:gen-syms [z] :eval-syms [y]}
    ...))
```

## License

Copyright © 2017 Viktor Magyari

Distributed under the Eclipse Public License v1.0.
