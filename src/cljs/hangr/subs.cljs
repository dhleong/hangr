(ns ^{:author "Daniel Leong"
      :doc "Subscriptions"}
  hangr.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]
            [hangr.util.conversation :refer [event-incoming? fill-members insert-hangr-events]]))

;; -- Helpers -----------------------------------------------------------------

;; -- Simple subscriptions ----------------------------------------------------
;; NOTE: keywords are functions! In this shortcut, they act on db
;;  to just return a specific key

(reg-sub :connecting? :connecting?)
(reg-sub :focused? :focused?)
(reg-sub :reconnecting? :reconnecting?)
(reg-sub :latest-version :latest-version)
(reg-sub :latest-version-notes :latest-version-notes)
(reg-sub :self :self)
(reg-sub :pending-image :pending-image)
(reg-sub :people :people)

;; -- Internal subscriptions --------------------------------------------------
;; Note the use of namespaced keywords to get the raw field. Actual
;;  subscriptions should use the non-namespaced version to get the
;;  appropriate "view"

(reg-sub ::convs :convs)
(reg-sub ::page :page)

;; -- Public subscriptions ----------------------------------------------------

(reg-sub
  :page
  :<- [:connecting?]
  :<- [:self]
  :<- [::convs]
  :<- [::page]
  (fn [[connecting? self convs page] _]
    (cond
      ; still connecting; override the page
      ; HACK: only show "connecting" for the friends list
      (and (= :friends (first page)) connecting?) 
      [:connecting]
      ; still loading core data; override the page
      ; (if it's not :about)
      (and (not= [:about] page)
           (or (nil? self) (nil? convs)))
      [:loading]
      ; ready to go!
      :else 
      page)))

(reg-sub
  :convs
  :<- [:self]
  :<- [::convs]
  :<- [:people]
  (fn [[self convs people] _]
    (when (and self convs)
      (->> convs
           vals
           ;; filter out conversations we're not part of
           (filter
             (fn [conv]
               (contains?
                 (set (keys (:members conv)))
                 (:id self))))
           ;; ... or that are archived
           (remove :archived?)
           ;; sort with most recent first
           (sort-by
             (fn [conv]
               (long (-> conv :events last :timestamp)))
             ;; compare in reverse order (higher timestamps first)
             #(compare %2 %1))
           ;; fill out the members
           (map (partial fill-members people))))))

; get the raw conversation object; useful for testing
(reg-sub
  ::conv
  :<- [::convs]
  (fn [convs [_ id]]
    (get convs id)))

(reg-sub
  :conv
  :<- [:self]
  :<- [::convs]
  :<- [:people]
  (fn [[self convs people] [_ id]]
    (when (and self convs)
      (-> convs
          (get id)
          ;; insert :incoming? key as appropriate
          (update-in
            [:events]
            (partial
              map
              #(assoc %
                      :incoming?
                      (event-incoming? (:id self) %))))
          (as-> c
            (fill-members people c))
          insert-hangr-events))))
