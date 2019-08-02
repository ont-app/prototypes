(ns ont-app.prototypes.core-test
  {:vann/preferredNamespacePrefix "test"
   :vann/preferredNamespaceUri "http://example.com/"
   }
  (:require [clojure.test :refer :all]
            [ont-app.igraph.core :refer [normal-form add]]
            [ont-app.igraph.graph :as g]
            [ont-app.prototypes.core :as proto]
            [ont-app.vocabulary.core :as voc]
            ))

(deftest ontology-test
  (testing "Ontology declaration should exist"
    (is (not (nil? proto/ontology)))))

(def test-graph
  (add proto/ontology
       [[:test/p1 :proto/aggregation :proto/Occlusive]
        [:test/Stage0
         :rdf/type :proto/Prototype
         :test/p1 :test/ValueInStage0
         :proto/hasParameter :test/testParameter]
        [:test/Stage1
         :proto/elaborates :test/Stage0
         :test/p1 :test/ValueInStage1]
        [:test/Stage2
         :proto/elaborates :test/Stage1
         :test/p1 :test/ValueInStage2
         :test/testParameter :test/ParameterInStage2
         ]
        ]))

(deftest agg-policies
  (testing "Aggregation policies"
    (is (= (proto/query-for-aggregation-policies test-graph)
           {:proto/hasParameter :proto/Transclusive,
            :proto/defaultAggregation :proto/Transclusive,
            :proto/elaborates :proto/Transclusive,
            :proto/aggregation :proto/Occlusive
            :test/p1 :proto/Occlusive,
            }
           ))

    (is (= (proto/get-description test-graph :test/Stage1)
           {
            :proto/hasParameter #{:test/testParameter}
            :test/p1 #{:test/ValueInStage1},
            :rdf/type #{:proto/Prototype}
            }))
           
    (is (= (proto/get-description test-graph :test/Stage2)
           {:test/testParameter #{:test/ParameterInStage2},
            :test/p1 #{:test/ValueInStage2},
            :rdf/type #{:proto/Prototype}}
           ))
    
    (let [g (proto/install-description test-graph :test/Stage2)]
      (is (= (g :test/Stage2)
             {:test/p1 #{:test/ValueInStage2},
              :test/testParameter #{:test/ParameterInStage2
                                    },
              :rdf/type #{:proto/Prototype}})))
    ))

#_(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
