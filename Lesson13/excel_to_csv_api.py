from fastapi import FastAPI, File, UploadFile, Response
import pandas as pd
from io import BytesIO
import warnings

# Игнорировать предупреждения openpyxl
warnings.filterwarnings("ignore", category=UserWarning, module="openpyxl")

app = FastAPI()

@app.post("/convert-excel-to-csv")
async def convert_excel_to_csv(file: UploadFile = File(...)):
    content = await file.read()
    excel_file = BytesIO(content)
    df = pd.read_excel(excel_file, engine='openpyxl')
    csv_data = df.to_csv(index=False).replace("\r\n", "\n")
    return Response(content=csv_data, media_type="text/csv")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)