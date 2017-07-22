(ns macrowbar.core
  #?(:clj (:refer-clojure :exclude [eval]))
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.pprint :as pprint]
            [macrowbar.core-macros :as core-macros])
  #?(:cljs (:require-macros macrowbar.core)))

;; Specs

(core-macros/compile-time
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

  (defn enforce-spec
    "Conforms the value to the spec, or throws if it cannot do so.
     Example:
       `(enforce-spec ::my-spec my-value)`"
    [spec value]
    (s/assert spec value)
    (s/conform spec value)))

;; Public api

(defmacro compile-time
  "In JVM ClojureScripts, it only emits the body at compile time. In other
   environments, it always emits."
  [& body]
  `(core-macros/compile-time ~@body))

(core-macros/compile-time
  (defmacro cljs?
    "Returns `true` if compiled for cljs, `false` otherwise. Expects the `&env` hidden
     macro argument as its argument."
    [env]
    `(boolean (:ns ~env)))

  #?(:cljs (require '[cljs.js :as cljs]
                    '[cljs.env :as env]))

  (defn eval
    "Evaluates the expression."
    [expr]
    #?(:clj
       (clojure.core/eval expr)
       :cljs
       (let [result (volatile! nil)]
         (cljs/eval env/*compiler*
                    expr
                    {:ns      (.-name *ns*)
                     :context :expr}
                    (fn [{:keys [value error]}]
                      (if error
                        (throw (js/Error. (str error)))
                        (vreset! result value))))
         @result)))

  #?(:clj
     (defn try-loading-compiling-ns
       "Tries to load the compiling ns as a clojure namespace."
       []
       (try
         (require (ns-name *ns*))
         (catch Exception e))))

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
    (let [{:keys [context body]}       (enforce-spec ::macro-context-args args)
          {:keys [gen-syms eval-syms]} context]
      `(with-evaluated ~(or eval-syms [])
         (with-gensyms ~(or gen-syms [])
           ~@body)))))
