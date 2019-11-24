(ns ont-app.prototypes.ont
  (:require
   [ont-app.igraph.core :as igraph :refer [add
                                           subtract
                                           traverse
                                           reduce-s-p-o
                                           ]]
   [ont-app.igraph.graph :as graph :refer [make-graph]]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph-vocabulary.core :as igv]
   [taoensso.timbre :as log]
   #?(:clj [ont-app.igraph-vocabulary.io :as igv-io])
   )
  #?(:cljs (:require-macros
            [ont-app.igraph-vocabulary.macros :refer [graph-source]]
            ))
  )

(voc/cljc-put-ns-meta!
 'ont-app.prototypes.ont
 {
  :vann/preferredNamespacePrefix "proto"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/prototypes/ont#"
  })

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FUN WITH READER MACROS
;;;;;;;;;;;;;;;;;;;;;;;;;;

;; JUST CODING UP THE ONTOLOGY IN NATIVE CLJC FOR NOW
;; TODO: revisit integration with RDF file formats
;; #?(:cljs
;;    ;; Call a macro to reify the contents of the edn as cljs...
;;    (def ontology-source (graph-source "edn/prototypes.edn"))
;;    )

;; (defn read-ontology []
;;   #?(:cljs (add (g/make-graph)
;;                 ontology-source)
;;      :clj
;;      (let [source "edn/prototypes.edn"]
;;        (igv-io/read-graph-from-source source))))

;; NO READER MACROS BELOW THIS POINT


;; ^{:doc "
;;   The supporting ontology for prototypes, as an Igraph.graph, using keyword
;;   identifiers interned per ont-app.vocabulary. "
;;   }
;; (defonce ontology
;;   (let []
;;     (voc/clear-caches!)
;;     (reduce-s-p-o igv/resolve-namespace-prefixes
;;                   (g/make-graph)
;;                   (read-ontology))))


(def ontology-ref (atom (make-graph)))

(defn update-ontology [to-add]
  (swap! ontology-ref add to-add))

(update-ontology
 [
  [:proto/Vocabulary
   :rdf/type :igraph/Graph
   :igraph/imports :igraph/Vocabulary
   ]
  [:igraph/Vocabulary
   :rdf/type :igraph/Graph
   :igraph/compiledAs igv/ontology
   ]
  [:proto/PrototypeClass
   :rdfs/subClassOf :rdf/Class
   :rdfs/comment """
  Refers to a class pertinent to prototypes.
        """;
   ]
  [:proto/Prototype
   :rdf/type :proto/PrototypeClass
   :rdfs/comment """
  Refers to objects which can be described by elaboration properties.
  """;

   ]
  [:proto/Property
   :rdfs/subClassof :rdf/Property
   :rdfs/comment """
        Refers to a property that affects the description of a
        prototype's referent.  
        """
   ]

  [:proto/elaborates
   :rdf/type :proto/ProtypeProperty
   :proto/aggregation :proto/Transclusive
   :rdfs/comment """
        <subordinate> proto:elaborates <superordinate>.
  
        Asserts that <superordinate> acquires properties from <superordinate>,
        subject to the proto:ElaborationPolicy of said properties.

    CODE SUPPORT: this drives a traversal from stage to stage, collecting
    values from the attatched properties based on their aggregation 
    policies.
    
        """
   :rdfs/domain :proto/Prototype
   :rdfs/range :proto/Prototype
   :owl/seeAlso :proto/AggregationPolicy
   ]
  
  [:proto/AggregationPolicy>
   :rdf/type  :proto/PrototypeClass
   :rdfs/comment """
  Refers to one of proto:Exclusive, proto:Inclusive, proto:Occlusive,
   proto:Transclusive
  
  Exclusive meaning that it is an error for elaborating prototypes to make new assertions 
  for this property.

  Inclusive meaning that assertions made by elaborating prototypes will be
  conjoined with other such assertions.

  Occlusive meaning that assertions made by elaborating prototypes override
  assertions made by superior nodes in the prototype lineage.

  Transclusive meaning that assertions made by elaborating prototypes do not
    attach to the referent, but rather inform the inference of the referent.
    
  """
   ]

  [:proto/Inclusive
   :rdf/type :proto/AggregationPolicy
   :rdfs/comment """
  Refers to an aggregation policy wherein assertions made by elaborating 
  prototypes will be conjoined with other such assertions within the 
  elaboration lineage. This is the default.
  """
   ]

  [:proto/Occlusive
   :rdf/type :proto/AggregationPolicy
   :rdfs/comment """
  Refers to an aggregation policy wherein assertions made by subordinate
  prototypes override assertions made by superior nodes in the elaboration
  chain.
  """
   ]

  [:proto/Transclusive
   :rdf/type :proto/AggregationPolicy
   :rdfs/comment """
        Refers to a policy wherein the property in question does not
        attach ot the resulting referent, but rather characterizes
        some aspect of the inference process. An example would be the
        proto:elaborates.  There is almost always some kind of code
        support required for a transclusive property.
        """
   ]

  [:proto/aggregation
   :rdf/type :proto/Property
   :proto/aggregation :proto/Occlusive
   :rdfs/domain :proto/Property
   :rdfs/range :proto/AggregationPolicy
   :rdfs/comment """
        This ensemble:
  <property> proto:hasAggregationPolicy <policy>.
  <parent> <property> <value>.
  <child> <elaborates> <parent>.
  
  Asserts that <policy> applies when inferring <value> for <child>.

  """
   ]

  [:proto/defaultAggregation
   :rdf/type :proto/Property
   :proto/aggregation :proto/Transclusive
   :rdfs/domain :rdf/Property
   :rdfs/range :proto/AggregationPolicy
   :rdfs/comment """
        <p> proto:defaultAggregation <policy>
        asserts that <p> and its subproperties should be assigned 
        to aggregation policy <policy> unless otherwise unspecified.
        CODE SUPPORT:
        Generally speaking when inferring the description of a prototype, 
        there will be a case statement keyed to
        aggregation policies explicitly declared for various properties
        asserted for your prototype, and default aggregation will inform
        the default case. 
        """
   ]
  
  [:proto/hasParameter
   :rdf/type :Proto/Property
   :proto/aggregation :proto/Transclusive
   :rdfs/domain :proto/Prototype
   :rdfs/range :proto/Property
   :rdfs/comment """
        <prototype> proto:hasParameter <property>

    Asserts that <property> is schematic for the stages downstream
    from <prototype> , and that a fully specified description should
    specify at least one value for <property> to be result in a
    well-formed description.
        """
   ]

  [:proto/Function
   :rdfs/subClassOf :proto/Prototype
   :rdfs/subClassof :igraph/Function
   :proto/hasParameter :proto/argumentList
   ;; TODO: integrate arg list with clojure.spec
   ;; TODO: add parameter for output clojure.spec
   :proto/hasParameter :igraph/compiledAs
   :rdfs/comment "
Refers to the prototype of an executable function which plays some
part in defining some model.
"
   ]

  [:proto/argumentList
   :proto/aggregation :proto/Exclusive
   :igraph/domain :proto/Function
   :igraph/projectedRange :igraph/Vector
   :rdfs/comment "
Asserts an ordered set of parameters := [:?parameter-name ...]
"
   ]
  
  [:proto/function
   :rdfs/domain :proto/Prototype
   :rdfs/range :proto/Function
   :rdfs/comment "
<proto> :proto/function <fn>.
Asserts the URI of <fn> for <examplar> of <proto>
Where 
<exemplar> is a fully specified elaboration of <proto>
<fn> := [model this] -> <value>
<description> is the normal-form description of <exemplar>
"
   ]
  
  
  [:dc/description :proto/aggregation :proto/Occlusive]

  ])


(def ontology @ontology-ref)
