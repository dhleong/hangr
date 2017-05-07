(ns hangr.views.widgets-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.views.widgets :refer [icon]]))

(deftest icon-test
  (testing "Simple icon test"
    (is (= [:i.material-icons "send"] 
           (icon :send))))
  (testing "Icon with classes test"
    (is (= [:i.material-icons.is.awesome "send"] 
           (icon :send.is.awesome)))))

