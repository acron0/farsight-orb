(ns farsight_orb.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [farsight_orb.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'farsight_orb.core-test))
    0
    1))
