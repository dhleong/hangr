(ns hangr.util.parse-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.util.parse :refer [conv->clj]]))

(deftest conv->clj-test
  (testing "Basic test"
    (let [conv {:conversation
                {:conversation_id
                 {:id "conv-id"}
                 :self_conversation_state
                 {:self_read_state
                  {:participant_id
                   {:chat_id "malcolm"
                    :gaia_id "reynolds"}
                   :latest_read_timestamp 9001}}
                 :read_state
                 [{:participant_id
                   {:chat_id "its"
                    :gaia_id "kaylee"}
                   :last_read_timestamp 42}]}
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
                        :members {} 
                        :self
                        {:id :malcolm|reynolds
                         :latest-read-timestamp 9001}
                        :read-states
                        {:its|kaylee
                         {:latest-read-timestamp 42}})
                 (dissoc :event)) 
             (conv->clj
               (clj->js conv)))))))

