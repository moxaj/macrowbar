(ns macrowbar.core-macros
  (:require [macrowbar.util :as util])
  #?(:cljs (:require-macros macrowbar.core-macros)))

(defmacro emit
  "In Clojure and self-hosted ClojureScript, it always emits the body. In JVM ClojureScript,
   it only emits if the closure constant `macrowbar.util/DEBUG` is set and the `mode`
   argument is `:debug`."
  [mode & body]
  #?(:clj  (if-not (:ns &env)
             ;; Clojure
             `(do ~@body)
             ;; JVM ClojureScript
             (when (= :debug mode)
               `(when util/DEBUG
                  (do ~@body))))
     :cljs ;; Self-hosted ClojureScript
           `(do ~@body)))
