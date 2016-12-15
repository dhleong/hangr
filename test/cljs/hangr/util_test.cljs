(ns hangr.util-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [cljs.nodejs :as node]
            [hangr.util :refer [js->real-clj join-sorted-by]]))

(deftest js->real-clj-test
  (testing "simple object"
    (is (= {:foo "bar"} 
           (js->real-clj 
             #js {:foo "bar"})))))

(deftest join-sorted-by-test
  (testing "Join two collections"
    (is (= [{:key 1}
            {:key 2}
            {:key 3}
            {:key 4}
            {:key 5}
            {:key 6}]
           (join-sorted-by
             :key
             [{:key 1}
              {:key 3}
              {:key 5}]
             [{:key 2}
              {:key 4}
              {:key 6}])))))
