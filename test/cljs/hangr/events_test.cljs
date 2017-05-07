(ns hangr.events-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [re-frame.registrar :refer [get-handler]]
            [hangr.events]
            [re-frame.core :refer [dispatch subscribe reg-fx]]
            [day8.re-frame.test :as rf-test]))

(deftest send-message-test
  (testing "Verifying the :send-html flow"
    (rf-test/with-temp-re-frame-state
      (reg-fx :ipc identity)
      (reg-fx :check-unread identity)
      (reg-fx :close-window! identity)
      (let [conv-sub (subscribe [:hangr.subs/conv "mreynolds"])]
        (rf-test/run-test-sync
          ; initial state
          (dispatch [:update-conv
                     {:id "mreynolds"
                      :conversation {:state :1}
                      :events [{:val :1} {:val :2} {:val :3}]}])
          (is (= [:1 :2 :3]
                 (map :val (:events @conv-sub))))

          ; send a message
          (dispatch [:send-html "mreynolds"
                     "Your mouth is talking, Jayne."])
          (let [ev (:events @conv-sub)
                pending (last ev)]
            (is (= 4 (count ev)))
            (is (string? (:client-generated-id pending))))

          (let [cgid (-> @conv-sub :events last :client-generated-id)]
            ; receive a conv state update
            (dispatch [:update-conv
                       {:id "mreynolds"
                        :conversation {:state :2}
                        :events [{:val :1} {:val :2} {:val :3}]}])
            ; don't lose the pending:
            (is (= :2 (-> @conv-sub :conversation :state)))
            (is (= 4 (count (:events @conv-sub)))) ; FIXME

            ; finish sending
            (dispatch [:update-sent
                       "mreynolds"
                       {:self_event_state
                        {:client_generated_id cgid}
                        :val :sent}])
            (let [ev (:events @conv-sub)]
              (is (= 4 (count ev)))
              (is (= [:1 :2 :3 :sent]
                     (map :val ev))))))))))
