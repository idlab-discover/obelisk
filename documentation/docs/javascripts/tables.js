document$.subscribe(function() {
    var tables = document.querySelectorAll("article table")
    tables.forEach(function(table) {
        if (table.querySelectorAll("th.sortable").length > 0) {
            new Tablesort(table)
        }
    })
})