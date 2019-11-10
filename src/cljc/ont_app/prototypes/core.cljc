(ns ont-app.prototypes.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   ;; 3rd partly libs
   [taoensso.timbre :as log]
   ;; ont-app libs
   [ont-app.igraph.core :as igraph :refer [add subtract traverse reduce-s-p-o]]
   [ont-app.igraph.graph :as g]
   [ont-app.vocabulary.core :as voc]
   [ont-app.prototypes.ont :as ont]   
   [ont-app.igraph-vocabulary.core :as igv :refer [mint-kwi]]
   )

  )

(voc/cljc-put-ns-meta!
 'ont-app.prototypes.core
 {
  :voc/mapsTo 'ont-app.prototypes.ont
  }
 )
(def ontology
  "The supporting ontology for prototypes, as an Igraph.graph, using keyword
   identifiers interned per ont-app.vocabulary. "
  ont/ontology)


(declare aggregation-policy-cache)

(defn clear-caches! []
  "SIDE EFFECT: resets caches to initial state."
  (reset! aggregation-policy-cache {})
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FUN WITH READER MACROS
;;;;;;;;;;;;;;;;;;;;;;;;;;
#?(:cljs
   (enable-console-print!)
   ;; (defn on-js-reload [])
   )

;; TODO: ex-data should obviate this
(defn error [msg] #?(:clj (Error. msg)
                     :cljs (js/Error msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NO READER MACROS BELOW THIS POINT
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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
  <context> := {:on-missing-parameter {<parameter> <handler>, ...},
                :p-history #{<addressed-property>, ...}
               }
  <handler> := (fn [acc] ...) -> <acc'>, special action in case of a missing
    parameter. This might for example infer a value from other values in acc
  <addressed-property> is a property which was encounted and dealt with at some
    downstream stage, and is therefore not an open parameter. This is
    only neccessary if the for-properties of the calling elaborate is not
    :all, and includes the hasParameter property.
  "
  [aggregation-policy context acc p os]
  {:pre [(map? acc)]
   }
  (letfn [(get-missing-parameter-handler [parameter]
            (if-let [on-missing-parameter (:on-missing-parameter context)]
              (on-missing-parameter parameter)
            ))
          
          (collect-parameters
            [acc parameter]
            
            (if (or (acc parameter)
                    ((into #{} (:p-history context))
                     parameter))
              ;; acc has a spec for <parameter>. we're good
              acc
              ;; else acc doesn't have a spec for parameter
              (if-let [handler (get-missing-parameter-handler parameter)]
                (let [it (handler acc parameter)]
                  (when (= it acc)
                    (log/warn "Hander for " parameter "made no change"))
                  it)
                ;;else no spec and no handler
                (do
                  #_(log/debug "No value and no handler supplied for parameter "
                            parameter)
                  ;; Pass it through as an unsatisfied parameter
                  (assoc acc
                         :proto/hasParameter
                         (set/union (or (acc :proto/hasParameter #{}))
                                    #{parameter}))))))]
    
    (case (or (aggregation-policy p) :proto/Inclusive) ;;default
      
      :proto/Exclusive
      (if (and (acc p) (not (= (acc p) os)))
        (throw (ex-info (str "Exclusive aggregation violation:"
                            p
                            "/"
                            (acc p)
                            "/" os)
                        {:type ::ExclusiveAggregationViolation
                         :property p
                         :downstream-value (acc p)
                         :upstream-value os}))
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

(def elab-trace (atom []))
^:traversal-fn
(defn elaborate
  "
  Returns `[context' acc' q']` traversing proto:elaborates links, traversing `g`, informed by `context` and the aggregation policy of each property asserted for
  each stage in the elaboration chain. Optionally addressing only  `for-properties`
  Where
  <context> {:on-missing-parameter ... :p-history}
  <acc> is a p-o map for the referent described by the prototype described
    in the elaboration chain in <q>
  <q> is a queue of stages in the elaboration chain for some prototype, starting
    with the most specific stage.
  <for-properties> := :all or #{<target-property>, ...} (defaults to :all)
    when specified, the traversal will only include results for these props.
  <aggregation-policy> := {<p> Exclusive|Inclusive|Occlusive, ...}
  <on-missing-parameter> := {<parameter> <handler>, ...}
  <parameter> is a property such that <prototype> :proto/parameter  <property>
  <handler> := (fn [acc property] ...) -> <acc>
  <target-property> is a property to be focused on in the traversal to the
    exclusion of others.
  <p-history> := #{<addressed-property>, ...}
  <addressed-property> is a property which was encounted and dealt with at some
    downstream stage, and is therefore not an open parameter. This is
    only neccessary if the for-properties of the calling elaborate is
    :all, or includes the hasParameter property. Sub-properties (declared with
    rdfs/subPropertyOf fullfill parameters for their super-properties.

  "
  ;; TODO consider moving on-missing-parameter to the arg list.
  ([g context acc q]
   (elaborate :all g context acc q))
  
  ([for-properties g context acc q]
  {:pre [(or (= for-properties :all)
             (set? for-properties))
         (map? acc)
         ]
   }

   (let [stage (first q)
         sub-property-of* (igraph/transitive-closure :rdfs/subPropertyOf)
                           
         collect-super-properties (fn [parameters p]
                                    (reduce conj
                                            parameters
                                            (g p sub-property-of*)))

 
         update-context (fn [context desc]
                          (if (or (= for-properties :all)
                                  (for-properties :proto/hasParameter))
                            (assoc context
                                   :p-history (reduce
                                               collect-super-properties
                                               (into #{} (:p-history context))
                                               (keys desc)))

                            ;; else
                            context))
         ;; we may filter on one or more properties...
         sub-desc (if (= for-properties :all)
                    identity
                    (fn [desc]
                      (into (select-keys
                             (into {} desc)
                             for-properties))))
         
        ]
    [(update-context context (g stage))
     (reduce-kv (partial collect-prototype-properties
                         (get-aggregation-policy g)
                         context
                         )
                acc
                (sub-desc (g stage)))
     (reduce conj
             (rest q)
             (g stage :proto/elaborates))])))
   
(defn get-description 
  "Returns <description> of `prototype` defined in `g`, maybe using `context`
Where
<description> := {<p> #{<o>...}, ...}
<prototype> is the endpoint of some elaboration chain
<g> is a graph containing the elaboration chain and supporting
  declarations, such as property aggregation policies.
"
  ([g prototype context]
   (igraph/traverse g elaborate
                    {}
                    {}
                    [prototype]))
  ([g prototype]
   (get-description g prototype {})))

(defn install-description 
  "Returns <target>, adding the description inferred from  <prototype> in <source>.
  If <source> and <target> are the same (the default), <prototype> will be
      overwritten.
  Where
  <source> is a graph containing the elaboration chain and supporting
      declarations, such as property aggregation policies.
  <target> is any igraph. By default it is <source>.
  <prototype> is the endpoint of some elaboration chain in <source>
"
  ([g prototype]
   (install-description g prototype g)
   )
   ([source prototype target]
    (let [description (get-description source prototype)]
      (if (empty? description)
        (let []
          (log/warn "Empty description for " prototype)
          target)
        ;; else not empty
        (add (if (= source target)
               (subtract target [prototype])
               target)
             {prototype description})))))



(defn proto-p [p]
  "Returns (fn [model context acc queue]...) -> [context acc' (rest queue)]
   (a traversal function)
Where
<model> implements IGraph and uses ont-app.prototype
<context> is not referenced except by traverse
<acc> is a sequence
<acc'> Has had the set of objects inferred for (model (first q) <p>)
<queue> := [<stage>, ...]
<stage> is a stage of elaboration in <model>
"
  (letfn [(get-objects [desc] (into #{} (p desc)))]
    (fn [model context acc queue]
      [context
       (->> (traverse model
                     (partial elaborate #{p})
                     context
                     {}
                     [(first queue)])
           (get-objects)
           (into acc))
       (rest queue)])))


;;;;;;;;;;;;;;;;;;;
;; COORDINATION
;;;;;;;;;;;;;;;;;;;;

(defmethod mint-kwi :proto/Align
  [head  _ source _ target _ sourceP _ targetP]
  (keyword (str/join "_" [(str head)
                          (name source)
                          (name target)
                          (name sourceP)
                          "To"
                          (name targetP)])))

(defn align [model linking-parameter source target & args]
  "Returns `model`', modified with coordiation annoations for `linking-parameter`  appropriate to `args`
Where
<model> implements the Prototypes vocabulary
<linking parameter> links <target> to <coordination>, 
  st. 
  <source> :proto/hasParameter <linkingParameter> 
  will implictly map to  <coordination> via:
  <coordination> :proto/coordinatingProperty <linkingParam>

<source> is the coordinator prototype
<target> is coordinated by <source>
<args> := [<source predicate> <target predicate>
<coordination> is an elaboration of proto:Coordination
"
  (letfn [(collect-properties [model [source-p target-p]]
            (let [alignment (mint-kwi :proto/Align
                            :proto/source source
                            :proto/target target
                            :proto/sourceProperty source-p
                            :proto/targetProperty target-p)
                  ]
              
              (add model
                   [[alignment
                    :proto/elaborates :proto/Align
                    :proto/source source
                    :proto/target target
                    :proto/sourceProperty source-p
                    :proto/targetProperty target-p
                    :proto/coordinatingProperty linking-parameter
                    ]
                    [source
                     :proto/alignsTo alignment]
                    ])))
          ]
    (assert (and (not (empty? args))
                 (even? (count args))))
    (reduce collect-properties model (partition 2 args))))

(defn project [model linking-parameter source target source-collection-p target-p]
  "Returns `model`, defining  a <projection>  from `source` to `target`
with <source>  linked to the <projection> with `linking-parameter`
and linked to some <collection> with `source-collection-p`, with each
<elt> in <collection> linked to <source> by `target-p`
Where
<model> is informed by the protypes vocabulary
<projection> describes a mapping from each element of <collection>
  to an instance of  <target>
<source> describes something associated with some <collection>
<target> describes something pertinent to each <elt> in <collection>
<collection> := [<elt>, ...]
<linking parameter> links <source> to <projection>, 
  when this is an open parameter, the projection has not yet been
  put into effect
<source-collection-p> is the property that links <source> to <collection>
<target-p> links <source> to each <target> for <elt>
"
  (let [projection (mint-kwi :proto/Project
                             :proto/source source
                             :proto/target target
                             :proto/sourceProperty source-collection-p
                             :proto/targetProperty target-p)

        ]
    (add model
         [[projection
           :proto/elaborates :proto/Projection
           :proto/source source
           :proto/target target
           :proto/sourceProperty source-collection-p
           :proto/targetProperty target-p
           :proto/coordinatingProperty linking-parameter
           ]
          [source
           :proto/projects projection
           ]
          ])
    ))

