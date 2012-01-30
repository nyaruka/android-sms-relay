Android SMS Relay
=================

This is a simple SMS relayer.  It essentially turns your Android phone into an SMS modem that will relay messages to an HTTP server of your choice and vice versa.

We use this quite a bit in our work, so we thought we'd open it up to the world.  Note that this particular codebase is still pretty Alpha and we've been cleaning it up for the outside world.  It very well might break, but we appreciate any and all testing and bug reports.

Things that make it different than other solutions:

* we are backed by a database for persistence, we think that makes it less likely a message will be dropped in the case of power outages, reboots, or other issues
* we have a prettier message UI than the other guys
* easy integration with our RapidSMS httprouter module. (http://github.com/nyaruka/rapidsms-httprouter)

This is our first release, so there's still a lot we still have in store.  Pull requests happily accepted.

TODO
=====

* Allow user to configure a regex for which messages to handle (ie, only those starting with a particular keyword)
* Allow configuration of the handset via a file attachment, could make client configuration easier
* Lots of reliability / paranoia updates to make sure a message never gets lost
* Better icons / assets, less Nyaruka

CHANGELOG
==========

0.0.2
-------
* try to ping google.com before accessing server, if it fails, flip our WiFi state (from off to on or vice versa) then try again, restoring the state at the end
* trim our database to most recent 100 messages which have been handled
* fix a nasty bug with deliveries
* add RapidSMS endpoint configuration
* other little fixes

0.0.1
--------
* Initial release