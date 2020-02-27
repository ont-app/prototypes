(ns ont-app.prototypes.core-test
  (:require #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest is testing]]
               )
            [ont-app.graph-log.core :as glog]
            [ont-app.igraph.core :as igraph
             :refer [add
                     normal-form
                     subtract]]
            [ont-app.igraph.graph :as g]
            [ont-app.igraph-vocabulary.core :as igv]
            [ont-app.prototypes.core :as proto
             :refer [collapse
                     get-description
                     proto-p
                     ]
             ]
            [ont-app.vocabulary.core :as voc]
            ))

(def the igraph/unique)

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
    (is (= (proto/query-for-aggregation-policies test-graph))
        {
         :dc/description :proto/Occlusive
         :proto/aggregation :proto/Occlusive,
         :proto/argumentList :proto/Exclusive,
         :proto/coordinatingProperty :proto/Occlusive,
         :proto/defaultAggregation :proto/Transclusive,
         :proto/elaborates :proto/Transclusive,
         :proto/elementDescription :proto/Occlusive,
         :proto/hasParameter :proto/Transclusive,
         :proto/projects :proto/Inclusive,
         :proto/sourceProperty :proto/Occlusive,
         :proto/target :proto/Occlusive,
         :proto/targetProperty :proto/Occlusive,
         :test/p1 :proto/Occlusive,
         }
        )
    (is (= (get-description test-graph :test/Stage1)
           {
            :proto/hasParameter #{:test/testParameter}
            :test/p1 #{:test/ValueInStage1},
            :rdf/type #{:proto/Prototype}
            }))
           
    (is (= (get-description test-graph :test/Stage2)
           {:test/testParameter #{:test/ParameterInStage2},
            :test/p1 #{:test/ValueInStage2},
            :rdf/type #{:proto/Prototype}}
           ))
    
    (let [g (collapse test-graph :test/Stage2)]
      (is (= (g :test/Stage2)
             {:test/p1 #{:test/ValueInStage2},
              :test/testParameter #{:test/ParameterInStage2
                                    },
              :rdf/type #{:proto/Prototype}})))
    ))


(deftest modulo
  (glog/log-reset!)
  (let [g (add test-graph
               [[:test/warJustification
                 :proto/aggregation :proto/Occlusive
                 ]
                [:test/Quaker
                 :test/attends :test/QuakerMeetings
                 :test/warJustification :test/Never
                 ]
                [:test/Republican
                 :test/attends :test/RepublicanConventions
                 :test/warJustification :test/WhenFightingCommunism
                 ]
                ])
        republican-quaker (glog/log-value
                           ::republican-quaker
                           (add g
                                [:test/Nixon
                                 :proto/elaborates :test/Quaker
                                 :proto/modulo :test/Republican]))
        quaker-republican (glog/log-value
                           ::quaker-republican
                           (add g
                                [:test/Nixon
                                 :proto/elaborates :test/Republican
                                 :proto/modulo :test/Quaker]))
        attends (proto/proto-p :test/attends)
        war-justification (proto/proto-p :test/warJustification)
        ]
    (testing "Resolve Nixon Diamond"
      
      (is (= (republican-quaker :test/Nixon attends)
             #{:test/QuakerMeetings
               :test/RepublicanConventions
               }))
      (is (= (the (republican-quaker :test/Nixon war-justification))
             :test/WhenFightingCommunism))
      
      (is (= (quaker-republican :test/Nixon attends)
             #{:test/QuakerMeetings
               :test/RepublicanConventions
               }))
      (is (= (the (quaker-republican :test/Nixon war-justification))
             :test/Never))
      )))
    
        
(def sub-property-test-graph
  (add proto/ontology
       [[:test/subProp
         :rdfs/subPropertyOf :test/testParameter]
        [:test/stage0
         :rdf/type :proto/Prototype
         :proto/hasParameter :test/testParameter
         :proto/hasParameter :test/otherParameter
         ]
        [:test/stage1
         :proto/elaborates :test/stage0
         :test/subProp 1
         ]]))
       
  
(deftest sub-property-support
  (testing "Sub-properties should satisfy parameters for their supers."
    (is (= (get-description sub-property-test-graph :test/stage1)
           {:test/subProp #{1},
            :rdf/type #{:proto/Prototype},
            ;; subProp fulfills testParameter...
            :proto/hasParameter #{:test/otherParameter}}))
    ))


;; PIZZA EXAMPLE

(def pizza-world
  (add proto/ontology
       [[:pizza/Pizza
         :rdf/type :proto/Prototype
         :proto/hasParameter :pizza/hasBase
         :proto/hasParameter :pizza/hasTopping
         ]
        [:pizza/hasBase
         :proto/aggregation :proto/Occlusive
         :rdfs/domain :pizza/Pizza
         :rdfs/range :pizza/Base
         :rdfs/comment "Asserts the kind of crust of some Pizza"
         ]
        [:pizza/hasTopping
         :proto/aggregation :proto/Inclusive
         :rdfs/domain :pizza/Pizza
         :rdfs/range :pizza/Topping
         :rdfs/comment "Asserts a Topping to put on some Pizza"
         ]
        [:pizza/Base
         :rdf/type :rdfs/Class
         :rdfs/comment "The crust of a pizza"
         ]
        [:pizza/ThickCrust
         :rdf/type :pizza/Base
         ]
        [:pizza/ThinCrust
         :rdf/type :pizza/Base
         ]
        [:pizza/Topping
         :rdf/type :rdfs/Class
         :rdfs/comment "Something put on a pizza"
         ]
        [:pizza/TomatoTopping 
         :rdf/type :pizza/Topping
         ]
        [:pizza/MozarellaTopping
         :rdf/type :pizza/Topping
         ]
        [:pizza/MushroomTopping
         :rdf/type :pizza/Topping
         ]
        [:pizza/SpicyBeefTopping
         :rdf/type :pizza/Topping
         ]
        [:pizza/AnchoviesTopping
         :rdf/type :pizza/Topping
         ]
        [:pizza/PineappleTopping
         :rdf/type :pizza/Topping
         ]
        [:pizza/HamTopping
         :rdf/type :pizza/Topping
         ]

        ;; types of pizza
        [:pizza/ThickCrustPizza
         :proto/elaborates :pizza/Pizza
         :pizza/hasBase :pizza/ThickCrust
         ]
        [:pizza/VegetarianPizza
         :proto/elaborates :pizza/ThickCrustPizza
         :pizza/hasTopping :pizza/MozarellaTopping
         :pizza/hasTopping :pizza/TomatoTopping
         :pizza/hasTopping :pizza/MushroomTopping
         ]
        [:pizza/CarnivorePizza
         :proto/elaborates :pizza/ThickCrustPizza
         :pizza/hasTopping :pizza/MozarellaTopping
         :pizza/hasTopping :pizza/SpicyBeefTopping
         :pizza/hasTopping :pizza/HamTopping
         :pizza/hasTopping :pizza/AnchoviesTopping
         ]
        [:pizza/PescatarianPizza
         :proto/elaborates :pizza/VegetarianPizza
         :pizza/hasTopping :pizza/AnchoviesTopping
         ]
        [:pizza/HawaiianPizza
         :proto/elaborates :pizza/ThickCrustPizza
         :pizza/hasTopping :pizza/hamTopping
         :pizza/hasTopping :pizza/pineappleTopping
         ]
        ]))

(def order-model ;; Extends pizza-world to deal with preparing orders
  (add pizza-world
       [[:pizza/PizzaSpec
         :proto/elaborates :pizza/Pizza
         :proto/hasParameter :pizza/diameter
         :proto/hasParameter :pizza/charge
         :rdfs/comment "Refers to an actual Pizza to be made and sold."
         ]
        [:pizza/Small
         :proto/elaborates :pizza/PizzaSpec
         :pizza/diameter 10
         :pizza/charge 1000
         ]
        [:pizza/Medium
         :proto/elaborates :pizza/PizzaSpec
         :pizza/diameter 12
         :pizza/charge 1200
         ]
        [:pizza/Large
         :proto/elaborates :pizza/PizzaSpec
         :pizza/diameter 14
         :pizza/charge 1400
         ]

        [:pizza/ExtraLarge
         :proto/elaborates :pizza/PizzaSpec
         :pizza/diameter 16
         :pizza/charge 1600
         ]
        [:pizza/Topping
         :pizza/charge 100
         :rdfs/comment "Each topping costs a buck"
         ]
        
        [:pizza/size
         :proto/aggregation :proto/Exclusive
         :proto/domain :pizza/Spec
         :proto/range :pizza/Size
         :rdfs/comment "Asserts the Size of some PizzaSpec"
         ]
        [:pizza/diameter
         :proto/aggregation :proto/Exclusive ;; can only be declared once
         :rdfs/domain :pizza/PizzaSpec
         :rdfs/range :xsd/Integer
         :rdfs/comment "Asserts the diameter of some Pizza in inches"
         ]
        [:pizza/charge
         :proto/aggregation :proto/Exclusive ;; can only be declared once
         :rdfs/domain :pizza/ProductOffering
         :rdfs/range :xsd/Integer
         :rdfs/comment "Asserts the charge for some Pizza in cents"
         ]
        [:pizza/ProductOffering
         :rdf/type :rdfs/Class
         :rdfs/comment "Refers to any item offered by the shop (and charged for)"
         ]
        [:pizza/Order
         :proto/hasParameter :pizza/order
         :proto/hasParameter :pizza/hasItem
         :proto/hasParameter :pizza/total
         ]
        [:pizza/order
         :proto/aggregation :proto/Exclusive
         :rdfs/domain :pizza/Order
         :rdfs/range :xsd/Integer
         ]
        [:pizza/hasItem
         :proto/aggregation :proto/Inclusive
         :proto/domain :pizza/Order
         :proto/range :pizza/PizzaSpec
         ]
        [:pizza/total
         :proto/aggregation :proto/Exclusive
         :proto/domain :pizza/Order
         :proto/range :xsd/Integer
         :rdfs/comment "Asserts the total charge for some Order in cents (these are all tax-free :-)"
         ]
        [:pizza/holdThe
         :proto/aggregation :proto/Inclusive
         :rdfs/domain :pizza/Pizza
         :rdfs/range :pizza/Topping
         :rdfs/comment "Do not include some Topping on some Pizza (Overrides hasTopping)"
         ]
        ]))


(defn finalize-order [orders order]
  "Returns `model`' modified to contain a complete description of `order-id`
Handling hold-the relations."
  (let [
        maybe-hold-toppings (fn [pizza g to-hold]
                              (glog/log ::starting-hold-toppings
                                        :log/pizza pizza
                                        :log/g g
                                        :log/to-hold to-hold)
                              (glog/log-value
                               ::hold-toppings-return
                               (igraph/subtract
                                g
                                [pizza :pizza/hasTopping to-hold])))
        has-charge (proto-p :pizza/charge)
        collect-topping-charges (fn [pizza g topping]
                                  (glog/log ::pt1)
                                  (let [charge (g pizza :pizza/charge)]
                                    (glog/log ::pt2)
                                    (add (subtract g [pizza :pizza/charge])
                                         [pizza
                                          :pizza/charge
                                          (+ charge
                                             (the (g topping has-charge)))])))
        
        finalize-item (fn [g item]
                        (as->
                            (collapse g item)
                            g
                          (reduce (partial maybe-hold-toppings item)
                                  g
                                  (glog/log-value
                                   ::hold-the
                                   (g item :pizza/holdThe)))
                          
                          (reduce (partial collect-topping-charges
                                           item)
                                  g
                                  (g item :pizza/hasTopping))))
                        
        ]
    
    (as-> (collapse orders order)
        g
      (reduce finalize-item g (g order :pizza/hasItem)))))



(deftest pizza-example
  (glog/log-reset!)
  (let [order-id 123
        order (igv/mint-kwi :pizza/Pizza :pizza/order order-id)
        pizza (igv/mint-kwi :pizza/Pizza
                            :pizza/order order-id
                            :pizza/item-number 1)
        todays-orders (add order-model
                           [[order
                             :rdf/type :pizza/Order
                             :pizza/order order-id
                             :pizza/hasItem pizza
                             ]
                            [pizza
                             :proto/elaborates :pizza/PescatarianPizza
                             :proto/modulo :pizza/Large
                             :pizza/holdThe :pizza/AnchoviesTopping
                             ]])
        finalized-orders (finalize-order todays-orders order)
        ]
    (testing "Put together a pizza order"
      (is (= (the (todays-orders order :pizza/order))
             order-id))
      (is (= (the (todays-orders order :pizza/hasItem))
             pizza))
      (is (= (todays-orders pizza (proto/proto-p :pizza/hasTopping))
             #{:pizza/AnchoviesTopping
               :pizza/MozarellaTopping
               :pizza/MushroomTopping
               :pizza/TomatoTopping
               }))
      (is (= (the (todays-orders pizza (proto/proto-p :pizza/diameter)))
             14))
      (is (= (the (todays-orders pizza (proto/proto-p :pizza/charge)))
             1400))
      #_(is (= (finalized-orders order)
             nil))

      )))


