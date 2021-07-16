package com.openfaas.function;

import com.openfaas.model.IHandler;
import com.openfaas.model.IResponse;
import com.openfaas.model.IRequest;
import com.openfaas.model.Response;

public class Handler extends com.openfaas.model.AbstractHandler {

    public IResponse Handle(IRequest req) {
        Response res = new Response();
	    res.setBody("Hello, world!");

	    return res;
    }

    public void getWebCal(HttpServletResponse response,
			@PathVariable("seasonStartYear") int seasonStartYear,
			@PathVariable("team") String team) throws DaoException,
			ParseException, IOException, ValidationException {

		createWebCal(response, seasonStartYear, team);

		response.flushBuffer();
	}

	private void createWebCal(HttpServletResponse response,
			int seasonStartYear, String team) throws ParseException,
			IOException, ValidationException {

		// Fixture[] matches = matchDao.load(seasonStartYear, team);
		Fixture[] matches = fixtureTemplate.getForEntity(MATCHCARD_SVC_BASE_URL
			+ "/search/findByHomeTeamIdOrAwayTeamIdAndSeason?season={}&homeTeamId={}&awayTeamId={}", 
			Fixture[].class, seasonStartYear, team, team).getBody();

		OutputStream out = response.getOutputStream();
		response.setContentType("text/calendar");

		net.fortuna.ical4j.model.Calendar icsCalendar = new net.fortuna.ical4j.model.Calendar();
		icsCalendar.getProperties().add(
				new ProdId("-//Events Calendar//iCal4j 1.0//EN"));
		icsCalendar.getProperties().add(CalScale.GREGORIAN);
		icsCalendar.getProperties().add(Version.VERSION_2_0);

		for (Fixture match : matches) {
			icsCalendar.getComponents().add(createEvent(match));
		}

		CalendarOutputter outputter = new CalendarOutputter();
		outputter.output(icsCalendar, out);

	}

	private VEvent createEvent(Fixture fixture) throws ParseException, SocketException {
		
		String eventName = String.format("Match: %s v %s", fixture.getHomeTeamName(), fixture.getAwayTeamName());

		// FixtureDetails fixture = fixtureDao.load(fixture.getExternalFixtureId());
		ResponseEntity<Club> clubResponse = clubTemplate.getForEntity(CLUBS_SVC_BASE_URL, Club.class, f.getHomeTeamId());
		Club c = clubResponse.getBody();
		Session matchSession = Util.resolveMatchSession(fixture, c);

		DateTime startDate = createDate(fixture.getMatchDate(), matchSession.getStart());
		DateTime endDate = createDate(fixture.getMatchDate(), "22:00");

		VEvent meeting = new VEvent(startDate, endDate, eventName);

		meeting.getProperties().add(new Uid(fixture.getMatchDate()+'_'+fixture.getHomeTeamName()+'_'+fixture.getAwayTeamName()));
		
		LinkedHashMap<String,String> seasons = seasonTemplate
			.getForObject(SEASONS_SVC_BASE_URL + "/seasons?sort=seasonStartYear,desc", LinkedHashMap.class);
		

		// String description = String.format(
		// 		"BDBL Badminton %s Match\nHome Team: %s\nAway Team: %s",
		// 		fixture.getLeague(),
		// 		fixture.getHomeTeamName(),
		// 		fixture.getAwayTeamName());

		// String address = fixture.getVenue();

		// meeting.getProperties().add(new Description(description));
		// meeting.getProperties().add(new Location(address));

		return meeting;
	}

	private DateTime createDate(String matchDate, String matchTime) throws ParseException {
		new SimpleDateFormat("yyyy-MM-dd").parse(matchDate);

		java.util.Calendar date = new GregorianCalendar();
		date.set(java.util.Calendar.YEAR, Integer.parseInt(matchDate.substring(0, 4)));
		date.set(java.util.Calendar.MONTH, 
				Integer.parseInt(matchDate.substring(5, 7)) - 1);
		date.set(java.util.Calendar.DAY_OF_MONTH, Integer.parseInt(matchDate.substring(8, 10)));
		date.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(matchTime.substring(0, 2)));
		date.set(java.util.Calendar.MINUTE, Integer.parseInt(matchTime.substring(3, 5)));
		date.set(java.util.Calendar.SECOND, 0);

		return new DateTime(date.getTime());
	}
}
