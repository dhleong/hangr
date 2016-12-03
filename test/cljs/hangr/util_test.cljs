(ns hangr.util-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [cljs.nodejs :as node]
            [hangr.util :refer [js->real-clj]]))

(deftest js->real-clj-test
  (testing "simple object"
    (is (= {:foo "bar"} 
           (js->real-clj 
             #js {:foo "bar"})))))


