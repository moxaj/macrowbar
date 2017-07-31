(ns macrowbar.core-macros
  (:require [macrowbar.util :as util])
  #?(:cljs (:require-macros macrowbar.core-macros)))

(defmacro compile-time-strict
  "In JVM ClojureScripts, it only emits the body at compile time. In other
   environments, it always emits."
  [& body]
  #?(:clj  (if-not (:ns &env)
             ;; Clojure
             `(do ~@body)
             ;; JVM ClojureScript
             nil)
     :cljs ;; Self-hosted ClojureScript
           `(do ~@body)))

(defmacro compile-time
  "Same as `compile-time`, but also emits if the `goog-define`'d `macrowbar.util/DEBUG`
   is set to `true`."
  [& body]
  #?(:clj  (if-not (:ns &env)
             ;; Clojure
             `(do ~@body)
             ;; JVM ClojureScript
             `(when util/DEBUG
                ~@body))
     :cljs ;; Self-hosted ClojureScript
           `(do ~@body)))
