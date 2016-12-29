(ns hangr.util.conversation-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [cljs.nodejs :as node]
            [hangr.util.conversation :refer [conv-event-incoming? event-incoming? 
                                             fill-members
                                             unread?
                                             conv-latest-read
                                             insert-hangr-events]]))

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

(deftest conv-latest-read-test
  (testing "Easy case: we have a timestamp"
    (let [conv {:read-states
                {:itskaylee
                 {:latest-read-timestamp 9001}}}]
      (is (= 9001 (conv-latest-read
                    conv
                    :itskaylee)))))
  (testing "No outgoing and no timestamp"
    (let [conv {:read-states
                {:itskaylee
                 {:latest-read-timestamp 0}}
                :events
                [{:sender :mreynolds
                  :timestamp 42}]}]
      (is (= 0 (conv-latest-read
                 conv
                 :itskaylee)))))
  (testing "No timestamp, but an outgoing message"
    (let [conv {:read-states
                {:itskaylee
                 {:latest-read-timestamp 0}}
                :events
                [{:sender :itskaylee
                  :timestamp 9001}
                 {:sender :mreynolds
                  :timestamp 42}]}]
      (is (= 9001 (conv-latest-read
                    conv
                    :itskaylee))))))

(deftest insert-read-indicators-test
  (testing "Insert for multiple"
    (let [inserted
          (insert-hangr-events
            {:members
             {:mreynolds
              {:id :mreynolds
               :latest-read-timestamp 30}
              :tammd
              {:id :tammd
               :latest-read-timestamp 20
               :typing :typing}
              :itskaylee
              {:id :itskaylee
               :latest-read-timestamp 10}}
             :events
             [{:sender :mreynolds
               :timestamp 15}
              {:sender :tammd
               :timestamp 20}]
             :self
             {:id :mreynolds}})]
      (is (= [{:sender :itskaylee
               :hangr-type :read-indicator
               :id "itskaylee-read"
               :typing nil
               :timestamp 10}
              {:sender :mreynolds
               :timestamp 15}
              {:sender :mreynolds
               :hangr-type :timestamp
               :id "stamp-15"
               :timestamp 15}
              {:sender :tammd
               :timestamp 20}
              {:sender :tammd
               :hangr-type :timestamp
               :id "stamp-20"
               :timestamp 20}
              {:sender :tammd
               :hangr-type :read-indicator
               :id "tammd-read"
               :typing :typing
               :timestamp 20}]
             (:events inserted))))))

(deftest fill-members-test
  (testing "Fill with no match"
    (let [conv {:read-states
                {:itskaylee
                 {:latest-read-timestamp 9001}}
                :members
                {:itskaylee
                 {:id :itskaylee
                  :focused? true}}}]
      (is (= {:itskaylee
              {:id :itskaylee
               :focused? true
               :latest-read-timestamp 9001}}
            (:members 
              (fill-members {}  conv))))))
  (testing "Fill with match"
    (let [conv {:read-states
                {:itskaylee
                 {:latest-read-timestamp 9001}}
                :members
                {:itskaylee
                 {:id :itskaylee
                  :focused? true}}}
          people {:itskaylee
                  {:id :itskaylee
                   :name "Kaylee"}}]
      (is (= {:itskaylee
              {:id :itskaylee
               :name "Kaylee"
               :focused? true
               :latest-read-timestamp 9001}}
            (:members 
              (fill-members people conv)))))))
