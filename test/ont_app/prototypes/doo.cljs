(ns ont-app.prototypes.doo
  (:require [doo.runner :refer-macros [doo-tests]]
            [ont-app.prototypes.core-test]
            ))

(doo-tests
 'ont-app.prototypes.core-test
 )
