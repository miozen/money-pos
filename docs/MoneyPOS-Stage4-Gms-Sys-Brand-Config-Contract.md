# MoneyPOS 阶段 4 GMS→SYS 品牌配置窄契约设计

本设计对应阶段 4.6.4a，只定义下一迁移切片的边界与兼容要求；不改变现有路由、表、Mapper 或前端。

## 当前边界与目标

`GmsBrandConfigController` 提供商品品牌的定价/会员券策略接口，但它直接操作 SYS 归属的 `SysBrandConfig` 与 `SysBrandConfigMapper`。配置数据还被 POS 商品查询和商品 Excel 导入读取，因此不能把 Mapper 直接迁到 GMS Controller 的替代位置。

目标边界如下：

```text
GMS Controller → GMS BrandPricingConfigService → SYS BrandConfigService → SysBrandConfigMapper
```

- `SysBrandConfigService` 是唯一注入 `SysBrandConfigMapper` 的服务，负责按 `brand` 查找和“存在则更新 / 不存在则插入”的持久化语义。
- `GmsBrandPricingConfigService` 只消费 SYS 的稳定策略 DTO，负责 GMS 路由的默认值和 JSON 兼容映射；它不接触 Mapper 或 `SysBrandConfig` Entity。
- Controller 只依赖 GMS 应用服务。`GoodsPosFacade` 与 `GmsGoodsExcelManager` 的既有 SYS Mapper 使用不在本切片内，避免把读模型/导入逻辑与控制器迁移混合。

## 窄 DTO 与操作

在共享 API 的中立 DTO 包新增两个场景类型，避免 SYS Service 依赖 `feature.gms`，也避免 GMS Controller 接收或返回 `SysBrandConfig` Entity：

| 类型 | 字段 | 用途 |
| --- | --- | --- |
| `BrandPricingPolicy` | `brand`、`couponEnabled`、`levelCodes`（逗号分隔字符串） | SYS 服务内部/跨边界的读取与保存载体。 |
| `BrandPricingPolicyView` | `couponEnabled`、`levelCodes`（`String[]` 或 `null`） | GMS GET 路由的前端兼容返回值。 |

建议服务操作：

```java
BrandPricingPolicy findByBrand(String brand);
void save(BrandPricingPolicy policy);
BrandPricingPolicyView getBrandPricingPolicy(String brandId);
void saveBrandPricingPolicy(BrandPricingPolicy policy);
```

前两项属于 SYS 服务；后两项属于 GMS 应用服务。保存 DTO 的 JSON 字段继续使用 `brand`、`couponEnabled`、`levelCodes`，因此前端 `saveConfig()` 无需改动。

## 必须保持的兼容语义

| 场景 | 现有行为 | 迁移后要求 |
| --- | --- | --- |
| 品牌尚无配置，GET | `couponEnabled=true`，`levelCodes=null` | 完全保持。 |
| 已有配置但 `levelCodes` 为空 | `couponEnabled` 取库值，`levelCodes=[]` | 完全保持。 |
| 已有配置且等级非空 | 按逗号拆成数组 | 完全保持，不过滤或重排 code。 |
| POST 已有品牌 | 仅更新 `couponEnabled`、`levelCodes`，保留原记录 ID、品牌、租户与审计字段 | 完全保持。 |
| POST 新品牌 | 原样插入提交的 `brand`、优惠开关与等级字符串 | 完全保持。 |
| 路由/权限/前端 | `GET`、`POST /gms/brand/config` 与既有前端调用 | 不改变。 |

## 验收与非目标

迁移切片须新增隔离数据库集成测试，覆盖上述五种读取/保存语义；随后运行全量 `mvn test`、`mvn package -DskipTests` 和 additions-only 门禁。Controller→Mapper 数量预期从 3 降至 2。

本切片不迁移 `GoodsPosFacade`、`GmsGoodsExcelManager` 的 `SysBrandConfigMapper` 使用，不改 `sys_brand_config` 表或 MyBatis Mapper，不把品牌配置与商品 Excel/价格矩阵重构合并，也不修改前端。
