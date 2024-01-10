SpotBot Discord Utility
=======================

SpotBot is a Discord utility designed for setting alerts based on the price of an asset reaching a specified range or crossing a trend line, or simply as a reminder for future events.

Asset Definition
----------------

An asset is represented by a pair of two tickers, such as ETH/USDT, ADA/BTC, EUR/USD, etc.

Range Alerts
----------

A box is a range defined by two prices, optionally with starting and ending dates during which the alert is active.

Trend Alerts
------------

A trend is a line defined by two points on a price chart, each having a price and a date or time as coordinates.

Remainder Alerts
----------------

Remainder alerts allow you to receive a notification with a pre-prepared message. This is useful for setting reminders for events you don't want to miss.

Price Format
------------

Prices can use either a comma or a dot separator and are expected to be positive. For example, 23.6 or 38.2 are valid.

Date Format
-----------

Dates should be provided in UTC date and time. Discord doesn't provide time zone information, so users need to consider this. The expected format is dd/MM/yyyy-HH:mm, for example, 10/01/2024-03:43.

Create a new Alert
--------

Use the following commands to set new alerts:

*   `/range`: Create a new range alert.
*   `/trend`: Create a new trend alert.
*   `/remainder`: Set a reminder for future events.

Margin
------

Both range and trend alerts can have a margin, which is a value in the currency of the watched asset, such as $1000 on the BTC/USD pair. The margin is added on both sides of a range or a trend to extend them. A pre-alert notifies you when the price reaches this zone.

Snooze
------

Range and margin alerts have two parameters: `repeat` and `repeat-delay`. These can be set using the commands of the same names.

*   `repeat`: The number of times a triggered alert will be rethrown (default: 10).
*   `repeat-delay`: The time in hours to wait before the alert can be raised again (default: 8 hours).

Channel Usage
-------------

This bot works exclusively in the channel #test of your Discord server. You can also use it from your private channel. When an alert occurs, the owner is notified on the channel where the alert was created. Alerts set using a private channel remain confidential.

You may join the role @SpotBot to get notified of each alert occurring on #test of your Discord server.

For a description of available commands, use:

bashCopy code

`/spotbot commands`

For example usages of these commands, use:

bashCopy code

`/spotbot examples`

### Commands

*   `/list`: List supported exchanges, pairs, or currently set alerts.
*   `/owner`: Show alerts defined by a specific user on a ticker or a pair.
*   `/pair`: Show alerts defined on a specific ticker or pair.
*   `/repeat`: Update the number of times an alert will be rethrown (0 to disable).
*   `/repeat-delay`: Update the delay to wait before the next repeat of the alert, in hours (0 will set to default 8 hours).
*   `/margin`: Set a margin for the alert that will warn once reached (0 to disable).
*   `/message`: Update the message shown when the alert is triggered.
*   `/delete`: Delete an alert (only the alert owner or an admin is allowed to do it).
*   `/remainder`: Set a remainder related to a pair to be triggered in the future.

Uptime
------

bashCopy code

`/uptime`

Returns the time since this bot is up.

Timezone Conversion
-------------------

bashCopy code

`/timezone`

Converts a date time into the UTC time zone, helping with commands that expect a date in UTC.

Parameters:

*   `zone`: 'now' to get the current UTC time, 'list' to get available time zones, or the time zone of your date.
*   `date`: (optional) A date to convert to UTC, expected format: dd/MM/yyyy-HH:mm.


Installation and Usage
----------------------


Support and Contributions
-------------------------

Feel free to open issues for bug reports, feature requests, or general feedback. Contributions via pull requests are also welcome.

License
-------

This project is licensed under the MIT License.
