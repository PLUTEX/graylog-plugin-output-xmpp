# XMPP output plugin for Graylog

[![Build Status](https://api.travis-ci.org/PLUTEX/graylog-plugin-output-xmpp.svg?branch=master)](https://travis-ci.org/PLUTEX/graylog-plugin-output-xmpp)

Graylog plugin to output messages to an XMPP server.

Installation
------------
[Download the plugin](https://github.com/PLUTEX/graylog-plugin-output-xmpp/releases)
and place the `.jar` file in your Graylog plugin directory. The plugin directory
is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

Configuration
-------------

![Configuration screen](screenshot-settings.png)

Besides the standard XMPP connection parameters, you can configure a resource
prefix that must be unique to each instance of the plugin using the same XMPP
account. This enables you to have multiple instances of the plugin (e.g. with
differing message formats) in the same cluster. This prefix is joined with the
anonymized Graylog node ID, so that multiple nodes in the cluster can establish
XMPP connections simultaneously.

License
-------

Copyright (c) 2018 PLUTEX GmbH.

Based largely on the [Jabber alarm callback plugin](https://github.com/graylog-labs/graylog-plugin-jabber), Copyright (c) 2014-2018 by Graylog, Inc.

This library is licensed under the GNU General Public License, Version 3.0.

See https://www.gnu.org/licenses/gpl-3.0.html or the LICENSE.txt file in this repository for the full license text.
