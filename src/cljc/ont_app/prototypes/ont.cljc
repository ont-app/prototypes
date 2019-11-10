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
   :rdfs/subClassOf :proto/AggregationPolicy
   :rdfs/comment """
  Refers to an aggregation policy wherein assertions made by elaborating 
  prototypes will be conjoined with other such assertions within the 
  elaboration lineage. This is the default.
  """
   ]

  [:proto/Occlusive
   :rdfs/subClassOf :proto/AggregationPolicy
   :rdfs/comment """
  Refers to an aggregation policy wherein assertions made by subordinate
  prototypes override assertions made by superior nodes in the elaboration
  chain.
  """
   ]

  [:proto/Transclusive
   :rdfs/subClassOf :proto/AggregationPolicy
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
        to aggregation policy <policy> if otherwise unspecified.
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

    Asserts that the stages downstream from <prototype> should specify at least
    one value for <property> to be result in a well-formed description.
        """
   ]

  [:proto/Function
   :rdfs/subClassOf :proto/Prototype
   :rdfs/subClassof :igraph/Function
   :proto/hasParameter :proto/argumentList
   ;; TODO: integrate arg list with clojure.spec
   ;; TODO: add parameter for output clojure.spec
   :proto/hasParameter :igraph/compiledAs
   :rdfs/comment "Refers to the prototype of an executable function which plays some part in defining some model."
   ]

  [:proto/argumentList
   :proto/aggregation :proto/Exclusive
   :igraph/domain :proto/Function
   :igraph/projectedRange :igraph/Vector
   :rdfs/comment "Asserts an ordered set of parameters := [:?parameter-name ...]"
   ]
  
  [:proto/function
   :rdfs/domain :proto/Prototype
   :rdfs/range :proto/Function
   :rdfs/comment "<proto> :proto/function <fn>.
Asserts the URI of <fn> for <examplar> of <proto>
Where 
<exemplar> is a fully specified elaboration of <proto>
<fn> := [model this] -> <value>
<description> is the normal-form description of <exemplar>
"
   ]

  [:proto/Coordination
   :proto/hasParameter :proto/source
   :proto/hasParameter :proto/target
   :proto/hasParameter :proto/coordinatingProperty
   :rdfs/comment "
Refers to a relationship between a source and target prototype where 
elaboration of the source implies some corresponding elaboration of the
target.
"   ]

  [:proto/Projector
   :proto/hasParameter :proto/sourceProperty
   :proto/hasParameter :proto/elementDescription
   :proto/hasParameter :proto/projects
   :rdfs/comment "
Refers a a prototype associated with a collection of elements from which we may project prototypes mapped to each element.
"
   ]
  [:proto/elementDescription
   :proto/aggregation :proto/Occlusive
   :rdfs/domain :proto/Projector
   :rdfs/range :proto/Prototype
   :rdfs/comment "
<Projector> elementDescription <prototype>

Asserts that <prototype> is an abstract description of each element in 
<collection>
"
   ]
  [:proto/projects 
   :rdfs/domain :proto/Projector
   :rdfs/range :proto/Projection
   :proto/aggregation :proto/Inclusive
   :rdfs/comment "
<projector> projects <projection>
Asserts that each element of some collection associated with <prototype> coordinates with <projection>, which specifies an abstract prototype for each such element, and other properties pertinent to the coordination."
   ]
  [:proto/Projection
   :proto/elaborates :proto/Alignment
   :proto/hasParameter :proto/projectionFn
   :rdfs/comment"
Refers to a coordination between some projector containing a collectiong, 
and some target description associated with each element of said collection.
"
   ]
  [:proto/Alignment
   :proto/elaborates :proto/Coordination
   :proto/hasParameter :proto/sourceProperty
   :proto/hasParameter :proto/targetProperty
   :rdfs/comment "
Refers to a coordination wherein a property in the source aligns to somee
corresponding property in the target.
"
   ]
  [:proto/sourceProperty
   :proto/aggregation :proto/Occlusive
   :rdfs/domain :proto/Coordination
   :rdfs/range :proto/Property
   :rdfs/comment
   "
Asserts the property in the source prototype the object of which serves as the value to be coordinated with the target of a coordination, elaboration of which should be propagated to the target.
"
   ]
  [:proto/target
   :proto/aggregation :proto/Occlusive
   :rdfs/domain :proto/Coordination
   :rdfs/range :proto/Prototype
   :rdfs/comment
   "
Asserts the target prototype of a coordination, which should be elaborated 
in light of elaborations to the source description.
"
   ]
  [:proto/coordinatingProperty
   :proto/aggregation :proto/Occlusive
   :rdfs/comment "
Asserts the property applying between the elaborated source
description and the coordination specification. One indicates a need
for said coordination by declaring it as a parameter of the source description.
"
   ]
  [:proto/targetProperty
   :proto/aggregation :proto/Occlusive
   :rdfs/comment "
Asserts the property applicable between fully coordinated source and target 
elaborations.
"
   ]
  
  [:dc/description :proto/aggregation :proto/Occlusive]

  ])

(def ontology @ontology-ref)
