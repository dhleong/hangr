(ns ^{:author "Daniel Leong"
      :doc "UI utils"}
  hangr.util.ui
  (:require [re-frame.core :refer [dispatch]]))

(defn click-dispatch
  "Returns an on-click handler that dispatches the given event
  and prevents the default on-click events"
  [event]
  (fn [e]
    (.preventDefault e)
    (dispatch event)))

