(ns ont-app.prototypes.ont
  (:require)
  (:require-macros
   [ont-app.igraph-vocabulary.macros :refer [graph-source]]
   ))

(def ontology-source (graph-source "edn/prototypes.edn"))

  
