package com.halo.app.everyday.organizer

class ScheduleCancellationPending : IllegalStateException("Wait for schedule cancellation before permanently removing this item")
class DuplicateCalendarUids : IllegalArgumentException("Selected calendars contain duplicate event UIDs. Export these calendars separately or select one version of each event")
