(ns macrowbar.core
  #?(:clj (:refer-clojure :exclude [eval]))
  (:require [clojure.spec.alpha :as s]
            [macrowbar.core-macros :as core-macros])
  #?(:cljs (:require-macros macrowbar.core)))

;; Specs

(core-macros/emit :debug
  (s/def ::symbols
    (s/coll-of symbol? :kind vector?))

  (s/def ::gen-syms ::symbols)

  (s/def ::bind-syms ::symbols)

  (s/def ::eval-syms ::symbols)

  (s/def ::with-syms-args
    (s/cat :syms (s/keys :opt-un [::gen-syms ::bind-syms ::eval-syms])
           :body (s/* any?)))

  (defn ^:private enforce-spec
    "Conforms the value to the spec, or throws if it cannot do so."
    [spec value]
    (let [conformed-value (s/conform spec value)]
      (when (= :clojure.spec.alpha/invalid conformed-value)
        (let [explained-data (s/explain-data spec value)]
          (throw (ex-info (str "Spec assertion failed:\n"
                               (with-out-str (s/explain-out explained-data)))
                          explained-data))))
      conformed-value)))

;; Public api

(defmacro emit
  "In Clojure and self-hosted ClojureScript, it always emits the body. In JVM ClojureScript,
   it if and only if emits the closure constant `macrowbar.util/DEBUG` is set to `true` and
   the `mode` argument is equal to `:debug`."
  [mode & body]
  `(core-macros/emit ~mode ~@body))

(core-macros/emit :debug-self-hosted
  (defn eval
    "Evaluates the expression. Assumes that `cljs.js` and `cljs.env` are already loaded. Expected
     to be used in a properly set up self-hosted environment (like Lumo or Planck)."
    [expr]
    #?(:clj
       (clojure.core/eval expr)
       :cljs
       (let [eval*    @(or (resolve 'cljs.js/eval)
                           (throw (js/Error. "Could not resolve 'cljs.js/eval")))
             compiler @(or (resolve 'cljs.env/*compiler*)
                           (throw (js/Error. "Could not resolve 'cljs.env/*compiler*")))]
         (let [result (volatile! nil)]
           (eval* compiler
                  expr
                  {:ns      (.-name *ns*)
                   :context :expr}
                  (fn [{:keys [value error]}]
                    (if error
                      (throw (js/Error. (str error)))
                      (vreset! result value))))
           @result)))))

(core-macros/emit :debug
  (defn cljs?
    "Returns `true` if compiled for cljs, `false` otherwise. Expects the `&env` hidden
     macro argument as its argument."
    [env]
    (boolean (:ns env)))

  (defmulti ^:private with-syms-impl (fn [body [type syms]] type))

  (defmethod with-syms-impl :gen
    [body [_ syms]]
    `(let [~@(mapcat (fn [sym]
                       [sym `(vary-meta (gensym ~(str sym "-")) merge ~(meta sym))])
                     syms)]
       ~body))

  (defmethod with-syms-impl :bind
    [body [_ syms]]
    (let [sym-map (into {} (map (juxt identity gensym) syms))]
      `(let [~@(mapcat (fn [[sym temp-sym]]
                         [temp-sym `(gensym '~sym)])
                       sym-map)]
         `(let [~~@(mapcat reverse sym-map)]
            ~(let [~@(mapcat identity sym-map)]
               ~body)))))

  (defmethod with-syms-impl :eval
    [body [_ syms]]
    `(let [~@(mapcat (fn [sym]
                       [sym `(eval ~sym)])
                     syms)]
       ~body))

  (defmacro with-syms
    "Utility macro for macros, expected to be used at compile time. Takes a map with optional
     keys `:gen`, `:bind` and `:eval` - each mapped to a vector of simple symbols - and any
     number of expressions. For each symbol mapped to:
      - `:gen`, it generates a new symbol
      - `:bind`, it evaluates it at runtime
      - `:eval`, it evaluates it at compile time.
     See the GitHub readme for a precise explanation of each of these features."
    [& args]
    (let [{:keys [body syms]} (enforce-spec ::with-syms-args args)]
      (reduce with-syms-impl
              `(do ~@body)
              syms))))
