$ ->
  ws = new WebSocket $("body").data("ws-url")
  ws.onmessage = (event) ->
    message = JSON.parse event.data
    switch message.type
      when "stockhistory"
        chart = $("<div>").addClass("chart").prop("id", message.symbol)
        stockHolder = $("<div>").addClass("stock-holder").attr("data-content",message.symbol).append(chart).click (event) ->
          # fetch stock details and tweet
        $("#stocks").append(stockHolder)
        plot = chart.plot([getChartArray(message.history)], getChartOptions(message.history)).data("plot")
      when "stockupdate"
        plot = $("#" + message.symbol).data("plot")
        data = getPricesFromArray(plot.getData()[0].data)
        data.shift()
        data.push(message.price)
        plot.setData([getChartArray(data)])
        
        yaxes = plot.getOptions().yaxes[0]
        if ((getAxisMin(data) < yaxes.min) || (getAxisMax(data) > yaxes.max))
          # reseting yaxes
          yaxes.min = getAxisMin(data)
          yaxes.max = getAxisMax(data)
          plot.setupGrid()

        plot.draw()
      else
        console.log(message)

getPricesFromArray = (data) ->
  (v[1] for v in data)

getChartArray = (data) ->
  ([i, v] for v, i in data)

getChartOptions = (data) ->
  series:
    shadowSize: 0
  yaxis:
    min: getAxisMin(data)
    max: getAxisMax(data)
  xaxis:
    show: false

getAxisMin = (data) ->
  Math.min.apply(Math, data) * 0.9

getAxisMax = (data) ->
  Math.max.apply(Math, data) * 1.1
