(ns hangr.util.conversation-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [cljs.nodejs :as node]
            [hangr.util.conversation :refer [conv-event-incoming? event-incoming? 
                                             unread?]]))

(deftest incoming?-test
  (testing "Event incoming"
    (is (true?
          (event-incoming?
            :mreynolds
            {:sender :itskaylee})))
    (is (true?
          (conv-event-incoming?
            {:self
             {:id :mreynolds}}
            {:sender :itskaylee}))))
  (testing "Event outgoing"
    (is (false?
          (event-incoming?
            :mreynolds
            {:sender :mreynolds})))
    (is (false?
          (conv-event-incoming?
            {:self
             {:id :mreynolds}}
            {:sender :mreynolds})))))

(deftest unread?-test
  (let [conv {:self
              {:id :mreynolds
               :latest-read-timestamp 9001}}]
    (testing "Unread Incoming"
      (is (unread?
            (assoc conv
                   :events
                   [{:sender :itskaylee
                     :timestamp 10000}]))))
    (testing "Hangout events don't count"
      (is (not
            (unread?
              (assoc conv
                     :events
                     [{:hangout_event {:type "END_HANGOUT"}
                       :timestamp 10000}])))))
    (testing "Outgoing don't count"
      (is (not
            (unread?
              (assoc conv
                     :events
                     [{:sender :mreynolds
                       :timestamp 10000}])))))))
