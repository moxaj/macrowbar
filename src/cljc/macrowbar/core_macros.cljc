(ns macrowbar.core-macros
  #?(:cljs (:require-macros macrowbar.core-macros)))

(defmacro compile-time
  "In JVM ClojureScripts, it only emits the body at compile time. In other
   environments, it always emits."
  [& body]
  (when #?(:clj  (not (:ns &env))
           :cljs true)
    `(do ~@body)))
