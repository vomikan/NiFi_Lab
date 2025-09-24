from nifiapi.flowfiletransform import FlowFileTransform, FlowFileTransformResult
import pandas as pd

class ExcelToCSVProcessor(FlowFileTransform):
    class Java:
        implements = ['org.apache.nifi.python.processor.FlowFileTransform']
    
    class ProcessorDetails:
        version = '0.0.1-SNAPSHOT'
        relationships = {
            "success": "FlowFiles that are successfully converted to CSV",
            "failure": "FlowFiles that could not be converted"
        }
    
    def __init__(self, **kwargs):
        pass
    
    def transform(self, context, flowfile):
        try:
            # Retrieve the contents of the FlowFile as bytes
            input_bytes = flowfile.getContentsAsBytes()  # Use getContentsAsBytes()
            
            # Convert bytes to a byte stream for pandas
            from io import BytesIO
            input_stream = BytesIO(input_bytes)
            
            # Read Excel data using pandas
            excel_data = pd.read_excel(input_stream, engine='openpyxl')
            
            # Convert Excel data to CSV format
            csv_data = excel_data.to_csv(index=False, encoding='utf-8')
            
            return FlowFileTransformResult(
                relationship="success",
                contents=csv_data,
                attributes={"converted_from": "excel", "mime.type": "text/csv"}
            )
        except Exception as e:
            return FlowFileTransformResult(
                relationship="failure",
                contents=str(e),
                attributes={"error_message": str(e)}
            )

# Register the processor
def get_processor():
    return ExcelToCSVProcessor()
