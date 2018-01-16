(ns macrowbar.core
  #?(:clj (:refer-clojure :exclude [eval]))
  (:require [clojure.spec.alpha :as s]
            [macrowbar.core-macros :as core-macros])
  #?(:cljs (:require-macros macrowbar.core)))

;; Specs

(core-macros/emit :debug
  (s/def ::macro-args
    (s/cat :args-decl (s/and (s/coll-of simple-symbol? :kind vector?)
                             #(= % (distinct %)))
           :body      some?
           :args      (s/* any?)))

  (s/def ::symbols
    (s/coll-of symbol? :kind vector?))

  (s/def ::with-gensyms-args
    (s/cat :syms ::symbols
           :body (s/* any?)))

  (s/def ::with-evaluated-args
    (s/cat :syms ::symbols
           :body (s/* any?)))

  (s/def ::gen-syms ::symbols)

  (s/def ::eval-syms ::symbols)

  (s/def ::macro-context-args
    (s/cat :context (s/keys :opt-un [::gen-syms ::eval-syms])
           :body    (s/* any?)))

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
   it only emits if the closure constant `macrowbar.util/DEBUG` is set and the `mode`
   argument is `:debug`."
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

  (defmacro with-gensyms
    "Executes each expression of `body` in the context of each symbol in `syms`
     bound to a generated symbol."
    [& args]
    (let [{:keys [syms body]} (enforce-spec ::with-gensyms-args args)]
      `(let [~@(mapcat (fn [sym]
                         (let [gensym-expr `(gensym ~(str sym "-"))]
                           [sym (if-some [sym-meta (meta sym)]
                                  `(vary-meta ~gensym-expr merge ~(meta sym))
                                  gensym-expr)]))
                       syms)]
         ~@body)))

  (defmacro with-evaluated
    "Executes each expression of `body` in the context of each symbol in `syms`
     bound to an **evaluated** value. Can be used to prevent accidental multiple
     evaluation in macros."
    [& args]
    (let [{:keys [syms body]} (enforce-spec ::with-evaluated-args args)]
      (let [sym-map (into {} (map (juxt identity gensym) syms))]
        `(let [~@(mapcat (fn [[sym temp-sym]]
                           [temp-sym `(gensym '~sym)])
                         sym-map)]
           `(let [~~@(mapcat reverse sym-map)]
              ~(let [~@(mapcat identity sym-map)]
                 ~@body))))))

  (defmacro macro-context
    "Macro helper function, the equivalent of `with-gensyms` + `with-evaluated`."
    [& args]
    (let [{:keys [context body]}
          (enforce-spec ::macro-context-args args)

          {:keys [gen-syms eval-syms]
           :or   {gen-syms [] eval-syms []}}
          context]
      `(with-evaluated ~eval-syms
         (with-gensyms ~gen-syms
           ~@body)))))
