const functions = require("firebase-functions");
const admin = require("firebase-admin");
const Alexa = require("ask-sdk-core");

admin.initializeApp();
const db = admin.firestore();

function parseShoppingInput(rawArticle, rawAnzahl, rawEinheit) {
  let text = (rawArticle || "").trim();
  let amount = rawAnzahl ? parseFloat(rawAnzahl) : null;
  let unit = (rawEinheit || "").trim().toLowerCase();
  let name = text;

  const wordNumbers = {
    "ein": 1, "eine": 1, "einen": 1, "einer": 1, "eins": 1,
    "zwei": 2, "drei": 3, "vier": 4, "fünf": 5, "sechs": 6,
    "sieben": 7, "acht": 8, "neun": 9, "zehn": 10,
    "elf": 11, "zwölf": 12, "fünfzig": 50, "hundert": 100,
    "einhundert": 100, "zweihundert": 200, "dreihundert": 300,
    "vierhundert": 400, "fünfhundert": 500
  };

  const unitMap = {
    "g": "g", "gramm": "g", "gr": "g",
    "kg": "kg", "kilo": "kg", "kilogramm": "kg",
    "ml": "ml", "milliliter": "ml",
    "l": "l", "liter": "l",
    "packung": "Packung", "packungen": "Packung", "pkg": "Packung", "pck": "Packung",
    "dose": "Dose", "dosen": "Dose",
    "flasche": "Flasche", "flaschen": "Flasche",
    "becher": "Becher",
    "beutel": "Beutel",
    "glas": "Glas", "gläser": "Glas",
    "stück": "Stück", "stk": "Stück"
  };

  if (unitMap[unit]) {
    unit = unitMap[unit];
  } else if (!unit) {
    unit = "Stück";
  }

  const digitMatch = text.match(/^(\d+(?:[\.,]\d+)?)\s*([a-zA-ZäöüÄÖÜß]+)?\s*(.*)$/i);
  if (digitMatch && digitMatch[3]) {
    amount = parseFloat(digitMatch[1].replace(",", "."));
    const possibleUnit = (digitMatch[2] || "").toLowerCase();
    name = digitMatch[3].trim();
    if (unitMap[possibleUnit]) {
      unit = unitMap[possibleUnit];
    } else if (possibleUnit) {
      unit = possibleUnit;
    }
  } else {
    const wordMatch = text.match(/^([a-zA-ZäöüÄÖÜß]+)\s+([a-zA-ZäöüÄÖÜß]+)?\s*(.*)$/i);
    if (wordMatch) {
      const firstWord = wordMatch[1].toLowerCase();
      if (wordNumbers[firstWord]) {
        amount = wordNumbers[firstWord];
        const possibleUnit = (wordMatch[2] || "").toLowerCase();
        if (unitMap[possibleUnit]) {
          unit = unitMap[possibleUnit];
          name = wordMatch[3].trim();
        } else if (possibleUnit && wordMatch[3]) {
          unit = possibleUnit;
          name = wordMatch[3].trim();
        } else {
          name = (wordMatch[2] + " " + (wordMatch[3] || "")).trim();
        }
      } else if (unitMap[firstWord]) {
        unit = unitMap[firstWord];
        name = (wordMatch[2] + " " + (wordMatch[3] || "")).trim();
      }
    }
  }

  if (!amount || isNaN(amount) || amount <= 0) {
    amount = 1.0;
  }

  const nameParts = name.split(/\s+/);
  if (nameParts.length > 1 && unitMap[nameParts[0].toLowerCase()]) {
    unit = unitMap[nameParts[0].toLowerCase()];
    name = nameParts.slice(1).join(" ");
  }

  if (name.length > 0) {
    name = name.charAt(0).toUpperCase() + name.slice(1);
  }

  return { name, amount, unit };
}

const LaunchRequestHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === "LaunchRequest";
  },
  handle(handlerInput) {
    console.log("Handling LaunchRequest!");
    const speechText = "Willkommen bei NutritionMVP! Was möchtest du auf deine Einkaufsliste setzen?";
    return handlerInput.responseBuilder
      .speak(speechText)
      .reprompt("Du kannst zum Beispiel sagen: Setze 2 Milch auf die Einkaufsliste.")
      .getResponse();
  }
};

const AddShoppingItemIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === "IntentRequest"
      && Alexa.getIntentName(handlerInput.requestEnvelope) === "AddShoppingItemIntent";
  },
  async handle(handlerInput) {
    console.log("Alexa AddShoppingItemIntent received!");
    const accessToken = handlerInput.requestEnvelope.context.System.user.accessToken;
    if (!accessToken) {
      console.log("No accessToken provided in request.");
      return handlerInput.responseBuilder
        .speak("Bitte verknüpfe zuerst dein NutritionMVP Konto in der Alexa App.")
        .withLinkAccountCard()
        .getResponse();
    }

    try {
      let uid;
      try {
        const decodedToken = await admin.auth().verifyIdToken(accessToken);
        uid = decodedToken.uid;
      } catch (authErr) {
        console.warn("verifyIdToken failed, falling back to decoding payload:", authErr.message);
        const parts = accessToken.split('.');
        if (parts.length === 3) {
          const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf8'));
          uid = payload.user_id || payload.sub;
        }
      }

      if (!uid) {
        return handlerInput.responseBuilder
          .speak("Deine Konto-Verknüpfung ist abgelaufen. Bitte verknüpfe dein Konto in der Alexa App neu.")
          .withLinkAccountCard()
          .getResponse();
      }

      console.log("Resolved UID:", uid);

      const slots = handlerInput.requestEnvelope.request.intent.slots;
      const rawArticle = slots && slots.Artikel ? slots.Artikel.value : null;
      const rawAnzahl = slots && slots.Anzahl ? slots.Anzahl.value : null;
      const rawEinheit = slots && slots.Einheit ? slots.Einheit.value : null;

      if (!rawArticle) {
        return handlerInput.responseBuilder
          .speak("Ich habe nicht verstanden, was auf die Einkaufsliste soll. Bitte versuche es noch einmal.")
          .reprompt("Was möchtest du auf die Liste setzen?")
          .getResponse();
      }

      const parsed = parseShoppingInput(rawArticle, rawAnzahl, rawEinheit);
      console.log("Parsed item:", parsed);

      let householdSnapshot = await db.collection("households")
        .where("members", "array-contains", uid)
        .limit(1)
        .get();

      if (householdSnapshot.empty) {
        householdSnapshot = await db.collection("households")
          .where("adminUid", "==", uid)
          .limit(1)
          .get();
      }

      if (householdSnapshot.empty) {
        console.warn("No household found for UID:", uid);
        return handlerInput.responseBuilder
          .speak("Du gehörst aktuell zu keinem Haushalt in NutritionMVP. Bitte erstelle zuerst einen Haushalt in der App.")
          .getResponse();
      }

      const householdId = householdSnapshot.docs[0].id;

      await db.collection("households")
        .doc(householdId)
        .collection("shopping_list")
        .add({
          name: parsed.name,
          amount: parsed.amount,
          unit: parsed.unit,
          isChecked: false,
          isAutoGenerated: false,
          isPantryItem: false,
          householdId: householdId,
          sourceName: "Alexa",
          weightGrams: 0.0,
          baseUnit: "g",
          category: "Sonstiges"
        });

      const speechText = parsed.unit && parsed.unit !== "Stück"
        ? `${parsed.amount} ${parsed.unit} ${parsed.name} wurde zur Einkaufsliste hinzugefügt.`
        : (parsed.amount > 1
            ? `${parsed.amount} mal ${parsed.name} wurde zur Einkaufsliste hinzugefügt.`
            : `${parsed.name} wurde zur Einkaufsliste hinzugefügt.`);

      return handlerInput.responseBuilder
        .speak(speechText)
        .getResponse();

    } catch (error) {
      console.error("Error inside AddShoppingItemIntentHandler:", error);
      return handlerInput.responseBuilder
        .speak("Es gab ein Problem beim Hinzufügen zur Einkaufsliste.")
        .getResponse();
    }
  }
};

const HelpIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === "LaunchRequest"
      || (Alexa.getRequestType(handlerInput.requestEnvelope) === "IntentRequest"
          && Alexa.getIntentName(handlerInput.requestEnvelope) === "AMAZON.HelpIntent");
  },
  handle(handlerInput) {
    const speechText = "Du kannst sagen: Setze 500g Hackfleisch oder 200 ml Sahne auf die Einkaufsliste.";
    return handlerInput.responseBuilder
      .speak(speechText)
      .reprompt(speechText)
      .getResponse();
  }
};

const CancelAndStopIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === "IntentRequest"
      && (Alexa.getIntentName(handlerInput.requestEnvelope) === "AMAZON.CancelIntent"
        || Alexa.getIntentName(handlerInput.requestEnvelope) === "AMAZON.StopIntent");
  },
  handle(handlerInput) {
    return handlerInput.responseBuilder
      .speak("Tschüss!")
      .getResponse();
  }
};

const FallbackIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === "IntentRequest"
      && Alexa.getIntentName(handlerInput.requestEnvelope) === "AMAZON.FallbackIntent";
  },
  handle(handlerInput) {
    const speechText = "Entschuldigung, das habe ich nicht verstanden. Sag zum Beispiel: Setze 200ml Sahne auf die Liste.";
    return handlerInput.responseBuilder
      .speak(speechText)
      .reprompt(speechText)
      .getResponse();
  }
};

const SessionEndedRequestHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === "SessionEndedRequest";
  },
  handle(handlerInput) {
    console.log("SessionEndedRequest reason:", handlerInput.requestEnvelope.request.reason);
    return handlerInput.responseBuilder.getResponse();
  }
};

const ErrorHandler = {
  canHandle() {
    return true;
  },
  handle(handlerInput, error) {
    console.error("Global Alexa Error Handler caught:", error);
    return handlerInput.responseBuilder
      .speak("Entschuldigung, es gab einen unerwarteten Fehler. Bitte versuche es erneut.")
      .getResponse();
  }
};

const skillBuilder = Alexa.SkillBuilders.custom()
  .addRequestHandlers(
    LaunchRequestHandler,
    AddShoppingItemIntentHandler,
    HelpIntentHandler,
    CancelAndStopIntentHandler,
    FallbackIntentHandler,
    SessionEndedRequestHandler
  )
  .addErrorHandlers(ErrorHandler);

const alexaSkill = skillBuilder.create();

exports.alexaWebhook = functions.https.onRequest(async (req, res) => {
  try {
    const response = await alexaSkill.invoke(req.body);
    res.status(200).json(response);
  } catch (err) {
    console.error("Alexa Error:", err);
    res.status(200).json({
      version: "1.0",
      response: {
        outputSpeech: {
          type: "SSML",
          ssml: "<speak>Entschuldigung, es gab einen Fehler.</speak>"
        }
      }
    });
  }
});
