(ns hangr.connection-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.connection :refer [conv->clj]]))

(deftest conv->clj-test
  (testing "Basic test"
    (let [conv {:conversation
                {:conversation_id
                 {:id "conv-id"}}
                :event
                [{:event_id "observed_123"}
                 {:event_id "456"
                  :sender_id
                  {:chat_id "malcolm"
                   :gaia_id "reynolds"}}]
                :foo "bar"}]
      (is (= (-> conv
                 (assoc :id "conv-id"
                        :events 
                        [{:event_id "456"
                          :id "456"
                          :sender :malcolm|reynolds
                          :sender_id
                          {:chat_id "malcolm"
                           :gaia_id "reynolds"}}]
                        :members [])
                 (dissoc :event)) 
            (conv->clj
              (clj->js conv)))))))

