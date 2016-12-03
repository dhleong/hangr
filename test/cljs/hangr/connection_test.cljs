(ns hangr.connection-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.connection :refer [conv->clj]]))

(deftest conv->clj-test
  (testing "Basic test"
    (let [conv {:conversation
                {:conversation_id
                 {:id "conv-id"}}
                :foo "bar"}]
      (is (= (assoc conv
                    :id "conv-id") 
            (conv->clj
              (clj->js conv)))))))

