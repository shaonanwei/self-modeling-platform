import sqlite3
import os

# 删除现有数据库文件
db_path = 'd:/DEMO/self-modeling-platform/backend/selfmodeling.db'
if os.path.exists(db_path):
    os.remove(db_path)
    print(f"Deleted existing database: {db_path}")

# 删除SHM和WAL文件
for ext in ['-shm', '-wal']:
    shm_path = db_path + ext
    if os.path.exists(shm_path):
        os.remove(shm_path)
        print(f"Deleted: {shm_path}")

# 连接新数据库并执行SQL脚本
conn = sqlite3.connect(db_path)
with open('d:/DEMO/selfmodeling-db-backup.sql', 'r', encoding='utf-8') as f:
    sql_content = f.read()
    conn.executescript(sql_content)
conn.close()
print('Database restored successfully')
