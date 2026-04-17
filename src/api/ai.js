export async function fetchAiAdvice({ userPrompt, resultData }) {
  const apiKey = import.meta.env.VITE_GEMINI_API_KEY;
  if (!apiKey) throw new Error('API key not configured');

  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;

  const promptText = `
You are an outdoor safety assistant. Analyze the following weather risk data and give the user friendly, concise advice.
User's planned activity: ${userPrompt || 'General outdoor activity'}
Overall Risk Level: ${resultData.level} (Score: ${resultData.score}/100)
Key Risk Factors: ${JSON.stringify(resultData.reasons)}
Historical Comparison: ${resultData.comparison_text}
Observed Raw Data: ${JSON.stringify(resultData.meta?.observed_raw || {})}

Provide a short, easy-to-understand safety recommendation in 2-3 paragraphs. Format with Markdown. Do not include raw JSON. Focus on actionable advice based on the weather conditions.
`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [{ parts: [{ text: promptText }] }]
    })
  });

  if (!response.ok) {
    const err = await response.json();
    throw new Error(err.error?.message || 'Failed to fetch AI advice');
  }

  const data = await response.json();
  return data.candidates[0].content.parts[0].text;
}