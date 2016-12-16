(ns hangr.node-runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [hangr.util-test]
              [hangr.util.conversation-test]
              [hangr.util.msg-test]
              [hangr.util.parse-test]))

(doo-tests 'hangr.util-test
           'hangr.util.conversation-test
           'hangr.util.msg-test
           'hangr.util.parse-test)
