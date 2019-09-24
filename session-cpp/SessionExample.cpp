#include "IOTDBSession.h"

using namespace std;
int main()
{
    Session *session = new Session("127.0.0.1", 6667, "root", "root");
    session->open();
    session->setStorageGroup("root.sg1");
    session->createTimeseries("root.sg1.d1.s1", TSDataType::INT64, TSEncoding::RLE, CompressionType::SNAPPY);
    session->createTimeseries("root.sg1.d1.s2", TSDataType::INT64, TSEncoding::RLE, CompressionType::SNAPPY);
    session->createTimeseries("root.sg1.d1.s3", TSDataType::INT64, TSEncoding::RLE, CompressionType::SNAPPY);
    string deviceId = "root.sg1.d1";
    vector<string> measurements;
    measurements.push_back("s1");
    measurements.push_back("s2");
    measurements.push_back("s3");
    for (int time = 0; time < 100; time++) 
    {

        vector<string> values;
        values.push_back("1");
        values.push_back("2");
        values.push_back("3");
        session->insert(deviceId, time, measurements, values);
    }
    vector<string> del;
    del.push_back("root.sg1.d1.s1");
    del.push_back("root.sg1.d1.s2");
    del.push_back("root.sg1.d1.s3");
    session->deletedata(del, 99);
    session->close();
}
