(ns ont-app.prototypes.core
  (:require
   [clojure.set :as set]
   ;; [clojure.tools.logging :as log]
   
   [ont-app.igraph.core :as igraph :refer [add subtract traverse reduce-s-p-o]]
   [ont-app.igraph.graph :as g]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph-vocabulary.core :as igv]
   [taoensso.timbre :as log]
   #?(:clj [ont-app.igraph-vocabulary.io :as igv-io])
   #?(:cljs [ont-app.prototypes.ont :as ont])
   ;; normal form translation of prototypes.ttl, generated at compile time
   ;; via macro and code in clj-based ont-app.igraph-vocabulary.io
   )

  )

(voc/cljc-put-ns-meta!
 'ont-app.prototypes.core
 {
  :vann/preferredNamespacePrefix "proto"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/prototypes/ont#"
  })

(declare aggregation-policy-cache)
(declare ontology-cache)
(defn clear-caches! []
  "SIDE EFFECTS: resets caches to initial state."
  (reset! aggregation-policy-cache {})
  (reset! ontology-cache nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FUN WITH READER MACROS
;;;;;;;;;;;;;;;;;;;;;;;;;;
#?(:cljs
   (enable-console-print!)
   ;; (defn on-js-reload [])
   )

(defn read-ontology []
  #?(:cljs (add (g/make-graph)
                ont/ontology-source)
     :clj
     (let [source "resources/edn/prototypes.edn"]
       (igv-io/read-graph-from-source source))))
  



(defn error [msg] #?(:clj (Error. msg)
                     :cljs (js/Error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NO READER MACROS BELOW THIS POINT
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ontology-cache (atom nil))
(defn ontology []
  "The supporting ontology for prototypes, as an Igraph.graph, using keyword
  identifiers interned per ont-app.vocabulary. 
  "
  (when-not @ontology-cache
    (reset! ontology-cache
            (read-ontology)))
  @ontology-cache)

(def prefixed voc/prepend-prefix-declarations)
  
(def agg-policies-sparql-query
  (prefixed
   "
  Select Distinct  ?p ?value 
  from {{graph}}
  where 
  {
    ?p proto:aggregation ?value.
  }
  "))

^:agg-policies-query
(def agg-policies-igraph-graph-query
  "Returns A query for aggregation policies from an IGraph.graph.
  "
  [[:?p :proto/aggregation :?value]])


(defmulti agg-policies-query
  "Returns a <policies-query> for Graph of `type`
  Where
  (query <graph> <policies-query>) -> #{<binding> ...}
  <graph> implements IGraph, and is of <type>
  <binding> := {:p ... :value ..., ...}
  <p> is a property
  <value> is the URI-kW of <p>'s aggregation policy.
  "
  ;; dispatched on...
  type)

{:doc "A graph-pattern query in igraph.graph format for aggreation policies.
Typically called in query-for-aggregation-policies
"}

(defmethod agg-policies-query
  (type (g/make-graph))
  [g]
  agg-policies-igraph-graph-query)


;; TODO: support other igraph implementations

(defn query-for-aggregation-policies
  "Returns {<property> <aggregation-policy>, ...} for declarations in `g`
  Where
  <property> is a property pertinent to some prototype
  <aggregation-policy> is one of :proto/Inclusive :proto/Occlusve :proto/Exclusve
    :proto/Transclusive.
  "
  [g]
  (letfn [(collect-bindings [acc b]
            (assoc acc
                   ;; SPARQL and IGRAPH query vars are a little different.
                   (or (:p b) (:?p b))
                   (or (:value b) (:?value b))))
          ]
    (let [_query (agg-policies-query g)]
      (reduce collect-bindings {}
              (igraph/query
               g
               _query)))))
      

(def aggregation-policy-cache
  "Caches the value of get-aggregation-policy
  "
  (atom {}))


^:reduce-fn
(defn get-aggregation-policy [g]
  "returns <policy-map> as specified in `g`
Where
<policy-map> := {<p> -> proto:Exclusive|proto:Inclusive|proto:Occlusive, ...} 
<g> is a graph containing all pertinent prototype properties.
<p> is a prototype property.
"
  (when (empty? @aggregation-policy-cache)
    (reset! aggregation-policy-cache
            (query-for-aggregation-policies g)))
  @aggregation-policy-cache)


^:reduce-kv-fn
(defn- collect-prototype-properties
  "
  Returns `acc`' adding `p` and `os` per (<aggregation-policy> `p`)
  Where
  <acc> := {<p> #{<o>..}, ...}, per the p-o map of a graph
  <p> is a property associated with some prototype <node>
  <os> := #{<o> ...}, values associated with <p> in <node>
  <aggregation-policy> := (fn [p] ...)
     -> proto:Exclusive|proto:Inclusive|proto:Occlusive}
     (typically implemented as a map)
  "
  [aggregation-policy on-missing-parameter acc p os]
  (letfn [(collect-parameters
            [acc parameter]
            (if (acc parameter)
              ;; acc has a spec for <parameter>. we're good
              acc
              ;; else acc doesn't have a spec for parameter
              (if-let [handler (on-missing-parameter parameter)]
                (let [it (handler acc parameter)]
                  (when (= it acc)
                    (log/warn "Hander for " parameter "made no change"))
                  it)
                ;;else no spec and no handler
                (do
                  (log/warn "No value and not handler supplied for parameter "
                            parameter)
                  ;; Pass it through as an unsatisfied parameter
                  (assoc acc
                         :proto/hasParameter
                         (set/union (or (acc :proto/hasParameter #{}))
                                    #{parameter}))))))]
    
    (case (or (aggregation-policy p) :proto/Inclusive) ;;default
      
      :proto/Exclusive
      (if (and (acc p) (not (= (acc p) os)))
        (throw (error (str "Exclusive aggregation violation:"
                            p
                            "/"
                            (acc p)
                            "/" os)))
        ;;else
        (assoc acc p os))
      
      :proto/Inclusive
      (assoc acc p (set/union (or (acc p) #{}) os))

      :proto/Occlusive
      (if (not (acc p))
        (assoc acc p os)
        acc)

      :proto/Transclusive ;; informs inference
      (if (= p :proto/hasParameter)
        ;; special case. May affect acc if the parameter hasn't been specified
        ;; yet and there's a handler, e.g.  Eponymous Graphs -> addressedTo
        (reduce collect-parameters acc os)

        ;;else it's not parameter
        acc))
      ))


(defn resolve-prototype
  "
  Returns `[context' acc' q']` traversing proto:elaborates links, traversing `g`, informed by `context` and the aggregation policy of each property asserted for
  each node in the elaboration chain.
  Where
  <context> {:on-missing-parameter ....}
  <acc> is a p-o map for the referent described by the prototype described
    in the elaboration chain in <q>
  <q> is a queue of nodes in the elaboration chain for some prototype, starting
    with the most specific node.
  <aggregation-policy> := {<p> Exclusive|Inclusive|Occlusive, ...}
  <on-missing-parameter> := {<parameter> <handler>, ...}
  <parameter> is a property such that <prototype> :proto/parameter  <property>
  <handler> := (fn [acc property] ...) -> <acc>
  
  "
  ;; TODO consider moving on-missing-parameter to the arg list.
  [g context acc q]
  (let [node (first q)
        ]
    [context 
     (reduce-kv (partial collect-prototype-properties
                         (get-aggregation-policy g)
                         (or (:on-missing-parameter context) {})
                         )
                acc (g node))
     (reduce conj
             (rest q)
             (g node :proto/elaborates))]))
   
(defn get-description [g prototype]
  "Returns <description> of <prototype> defined in <g>
Where
<description> := {<p> #{<o>...}, ...}
<prototype> is the endpoint of some elaboration chain
<g> is a graph containing the elaboration chain and supporting
  declarations, such as property aggregation policies.
"
  (igraph/traverse g resolve-prototype
                   {}
                   {}
                   [prototype]))

(defn install-description [g prototype]
  "Returns <g'>, replacing <prototype> in <g> with its description 
Where
<g> is a graph containing the elaboration chain and supporting
  declarations, such as property aggregation policies.
<prototype> is the endpoint of some elaboration chain
"
  (let [description (get-description g prototype)]
    (add (subtract g [prototype])
         {prototype description})))

(comment
  (igraph/traverse catalog resolve-prototype
                   {:on-missing-parameter {}} ;; context. handle :addressedTo
                   {} [:catalog/CatalogList])

  )

