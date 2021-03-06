'use strict';
// public/js/rss.js

/**
 * Javascript file for all the funtions used in the keyword configruation page.
 * 
 * @author       Tobias Mahncke
 * 
 * @version      6.0
 */

/**
 * Creates a bootstrap table to show the data
 */
function createTable() {
	var data = [];

	// Declare variables
	var filter, i, show;
	filter = $('#search').val().toUpperCase().trim();

	// Loop through all table rows, and hide those who don't match the search query
	for (i = 0; i < localData.rss.length; i++) {
		if ((localData.rss[i].link && localData.rss[i].link.toUpperCase().indexOf(filter) > -1) ||
			(filter && filter === '')) {
			data.push(localData.rss[i]);
			data[data.length - 1].button = '<button class="btn btn-danger pull-right" onClick="deleteRss(\'' + localData.rss[i].link + '\')"><span class="glyphicon glyphicon-minus" aria-hidden="true"></span></button>';
		}
	}

	// Split the data into four columns to use the space more effectively
	var tableData = [];
	for (i = 0; i < data.length; i += 4) {
		tableData[i / 4] = {
			link1: data[i].link,
			button1: data[i].button
		};
		if (data[i + 1]) {
			tableData[i / 4].link2 = data[i + 1].link;
			tableData[i / 4].button2 = data[i + 1].button;
		} else {
			tableData[i / 4].link2 = "";
			tableData[i / 4].button2 = "";
		}
		if (data[i + 2]) {
			tableData[i / 4].link3 = data[i + 2].link;
			tableData[i / 4].button3 = data[i + 2].button;
		} else {
			tableData[i / 4].link3 = "";
			tableData[i / 4].button3 = "";
		}
		if (data[i + 3]) {
			tableData[i / 4].link4 = data[i + 3].link;
			tableData[i / 4].button4 = data[i + 3].button;
		} else {
			tableData[i / 4].link4 = "";
			tableData[i / 4].button4 = "";
		}
	}

	$('#table').bootstrapTable('load', tableData);
}

/** 
 * Gets called by localData and creates the initial table
 */
function rssDataLoaded() {
	createTable();
}

/** 
 * Reloads the data after the upload finished. As the upload uses the standard HTML5 upload we cannot access the request so we delay by 20s and reload afterwards.
 */
function delayedReload() {
	showAlert("Daten werden aktualisiert...", Level.Info, 20000);
	setTimeout(function() {
		showAlert("Daten erfolgreich aktualisiert.", Level.Success, 1000);
		localData.reloadRSS(createTable);
	}, 20000);
}

/**
 * Deletes the specified rss url from the Database
 * @param rssUrl - the rss url to be deleted
 */
function deleteRss(rssUrl) {
	if (confirm('Möchten Sie den Feed "' + rssUrl + '" wirklich löschen. Das System wird dann nicht mehr in dieser Quelle suchen. Die bisherigen Daten bleiben in der Datenbank erhalten und können weiterhin eingesehen werden.')) {
		$.ajax({
			type: 'DELETE',
			url: '/api/rss',
			data: '{"link":"' + rssUrl + '"}',
			contentType: 'application/json'
		}).then(function() {
				localData.reloadRSS(function() {
					createTable();
					showAlert(rssUrl + ' gelöscht!', Level.Success, 2000);
				});
			},
			function(error) {
				showAlert(error.responseJSON.err.de, Level.Warning, 4000);
			});
	}
}

/**
 * Adds the rss from the input field to the database
 */
function postUrls() {
	var rssName = $('#rssName').val().trim();
	if (rssName === '') {
		showAlert('Keine leeren RSS Feeds erlaubt.', Level.Warning, 1000);
	} else {
		$.ajax({
			type: 'POST',
			url: '/api/rss',
			data: '{"link":"' + rssName + '"}',
			contentType: 'application/json'
		}).then(function() {
				localData.reloadRSS(function() {
					createTable();
					showAlert($('#rssName').val() + ' hinzugefügt!', Level.Success, 2000);
					$('#rssName').val('');
				});
			},
			function(error) {
				showAlert(error.responseJSON.err.de, Level.Warning, 4000);
			});
	}
}